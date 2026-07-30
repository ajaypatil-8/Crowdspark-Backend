-- src/main/resources/db/migration/V12__reward_tier_quantity_and_delivery.sql
-- Feature #24 (Reward Tier Claiming & Fulfillment) — closes a real gap found
-- during review: RewardTierRequest already accepts and validates
-- `estimatedDelivery` and `limitedQuantity` (see the Feature #25 comment in
-- that file), but the `reward_tiers` table — and the RewardTier entity —
-- never had columns to actually store them. Any creator who set "limited to
-- 50 units" on a reward tier had that value accepted by the API and then
-- silently discarded; nothing anywhere checked or decremented availability,
-- so a "limited" reward could be claimed by unlimited backers.
--
-- quantity_available is nullable: NULL means "unlimited" (no cap was ever
-- set), matching limited_quantity also being NULL. Only tiers that actually
-- set a limit get a non-null quantity_available, which is what
-- RewardTierServiceImpl / RewardClaimServiceImpl now check against.

ALTER TABLE reward_tiers
    ADD COLUMN IF NOT EXISTS estimated_delivery VARCHAR(100),
    ADD COLUMN IF NOT EXISTS limited_quantity   INTEGER,
    ADD COLUMN IF NOT EXISTS quantity_available INTEGER;

-- Defensive backfill: if limited_quantity is ever populated by some other
-- path without quantity_available also being set, default the remaining
-- count to the full limit. No-op today since both columns are brand new.
UPDATE reward_tiers
SET quantity_available = limited_quantity
WHERE limited_quantity IS NOT NULL AND quantity_available IS NULL;

ALTER TABLE reward_tiers
    ADD CONSTRAINT chk_reward_tier_quantity_non_negative
    CHECK (quantity_available IS NULL OR quantity_available >= 0);
