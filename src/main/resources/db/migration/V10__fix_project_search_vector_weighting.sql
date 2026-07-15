-- src/main/resources/db/migration/V10__fix_project_search_vector_weighting.sql
-- Feature #16 (Full-Text Search) — DB-level bug fix.
--
-- The database dump shows `update_project_search_vector()` as a single
-- unweighted call:
--     NEW.search_vector := to_tsvector('english', title || ' ' || short_description || ...)
-- instead of the weighted version in V3 (setweight A/title, B/short_description,
-- C/full_description, D/location). Proof: existing rows' stored search_vector
-- values have plain '<lexeme>:<position>' entries with no weight letter at all
-- (e.g. 'ai':2,9,27), whereas a weighted vector shows 'ai':2A,9B,27C. That
-- means ts_rank() cannot currently tell a title match from an incidental hit
-- deep in the full description, defeating "ranked by relevance" — the core
-- point of Feature #16 — regardless of the query-side fix in ProjectRepository.
--
-- This is deliberately a NEW migration rather than an edit to V3: once a
-- versioned migration has been applied anywhere, editing it risks a Flyway
-- checksum-mismatch failure on next boot for every environment that already
-- ran it. Re-asserting the fix forward (idempotently) is safe in every
-- scenario — whether V3's weighted version never actually ran here, or ran
-- and was later overwritten out of band.
--
-- Unlike V3's backfill (`WHERE search_vector IS NULL`), this backfill is
-- unconditional: existing rows already hold a non-NULL but incorrectly
-- unweighted vector, so a NULL-guarded backfill would skip them forever.

-- 1. Re-assert the correct, weighted trigger function (safe no-op if already correct).
CREATE OR REPLACE FUNCTION update_project_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.title, '')),            'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.short_description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.full_description, '')),  'C') ||
        setweight(to_tsvector('english', COALESCE(NEW.location, '')),          'D');
    RETURN NEW;
END;
$$;

-- 2. Re-attach the trigger (defensive drop + recreate, matching V3's own style).
DROP TRIGGER IF EXISTS trg_project_search_vector ON projects;

CREATE TRIGGER trg_project_search_vector
    BEFORE INSERT OR UPDATE OF title, short_description, full_description, location
    ON projects
    FOR EACH ROW EXECUTE FUNCTION update_project_search_vector();

-- 3. Recompute search_vector for ALL rows (not just NULL ones) so previously
--    unweighted vectors are corrected immediately after this migration runs.
UPDATE projects
SET search_vector =
    setweight(to_tsvector('english', COALESCE(title, '')),            'A') ||
    setweight(to_tsvector('english', COALESCE(short_description, '')), 'B') ||
    setweight(to_tsvector('english', COALESCE(full_description, '')),  'C') ||
    setweight(to_tsvector('english', COALESCE(location, '')),          'D');
