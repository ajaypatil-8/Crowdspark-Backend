// src/main/java/Crowdspark/Crowdspark/security/validation/PasswordStrengthValidator.java
// Feature #27 — Password Strength & Entropy Validation
//
// SCORING ALGORITHM (mirrors frontend passwordStrength.ts):
//   charsetSize = sum of active character pools
//     lowercase letters  → +26
//     uppercase letters  → +26
//     digits             → +10
//     special chars      → +32
//   rawEntropy = length × log₂(charsetSize)
//   penalties:
//     sequential run ≥3 (abc, xyz, 123) → ×0.85
//     repeated run   ≥3 (aaa, 111)      → ×0.75
//
//   Score bands:
//     < 28 bits  → VERY_WEAK  (rejected)
//     28–35 bits → WEAK       (rejected)
//     36–45 bits → FAIR       (minimum accepted)
//     46–59 bits → STRONG     (accepted)
//     ≥ 60 bits  → VERY_STRONG(accepted)
//
// COMMON-PASSWORDS BLACKLIST:
//   Top-200 passwords embedded as a static Set<String>.
//   Case-insensitive match against lowercased input.

package Crowdspark.Crowdspark.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class PasswordStrengthValidator implements ConstraintValidator<ValidPassword, String> {

    // ── Strength bands ────────────────────────────────────────────────────────

    public enum Strength { VERY_WEAK, WEAK, FAIR, STRONG, VERY_STRONG }

    public static Strength score(String password) {
        if (password == null || password.isEmpty()) return Strength.VERY_WEAK;

        int csz = 0;
        if (password.chars().anyMatch(Character::isLowerCase))                csz += 26;
        if (password.chars().anyMatch(Character::isUpperCase))                csz += 26;
        if (password.chars().anyMatch(Character::isDigit))                    csz += 10;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)))   csz += 32;

        if (csz == 0) return Strength.VERY_WEAK;

        double entropy = password.length() * (Math.log(csz) / Math.log(2));
        if (hasSequentialRun(password)) entropy *= 0.85;
        if (hasRepeatedRun(password))   entropy *= 0.75;

        if (entropy < 28) return Strength.VERY_WEAK;
        if (entropy < 36) return Strength.WEAK;
        if (entropy < 46) return Strength.FAIR;
        if (entropy < 60) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }

    // ── ConstraintValidator ───────────────────────────────────────────────────

    @Override
    public boolean isValid(String password, ConstraintValidatorContext ctx) {
        if (password == null) return true; // @NotBlank handles null

        // 1. Common-password check
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            fail(ctx, "This password is too common and easily guessable. Please choose something more unique.");
            return false;
        }

        // 2. Entropy check
        Strength s = score(password);
        if (s == Strength.VERY_WEAK) {
            fail(ctx, "Password is too weak. Use at least 8 characters with a mix of letters and numbers.");
            return false;
        }
        if (s == Strength.WEAK) {
            fail(ctx, "Password is weak. Add uppercase letters, numbers or symbols to make it stronger.");
            return false;
        }

        // FAIR, STRONG, VERY_STRONG all pass
        return true;
    }

    private void fail(ConstraintValidatorContext ctx, String message) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

    // ── Helper: sequential run of 3+ (abc, xyz, 123, cba) ────────────────────

    private static boolean hasSequentialRun(String pw) {
        for (int i = 0; i < pw.length() - 2; i++) {
            int a = pw.charAt(i), b = pw.charAt(i + 1), c = pw.charAt(i + 2);
            if (b - a == 1 && c - b == 1) return true; // ascending  e.g. a→b→c
            if (a - b == 1 && b - c == 1) return true; // descending e.g. c→b→a
        }
        return false;
    }

    // ── Helper: repeated character run of 3+ (aaa, 111) ──────────────────────

    private static boolean hasRepeatedRun(String pw) {
        for (int i = 0; i < pw.length() - 2; i++) {
            if (pw.charAt(i) == pw.charAt(i + 1) && pw.charAt(i + 1) == pw.charAt(i + 2)) {
                return true;
            }
        }
        return false;
    }

    // ── Top-200 common password blacklist (lowercase, static) ─────────────────

    private static final Set<String> COMMON_PASSWORDS = Set.of(
        "password","password1","password123","123456","12345678","123456789",
        "1234567890","qwerty","qwerty123","abc123","iloveyou","admin","letmein",
        "welcome","monkey","dragon","master","sunshine","princess","shadow",
        "superman","michael","football","baseball","soccer","hockey","tennis",
        "batman","trustno1","whatever","hello","charlie","donald","jessica",
        "password2","pass@123","pass1234","pass123","test","test123","111111",
        "1111111","11111111","000000","1234","12345","654321","987654321",
        "qwertyuiop","asdfghjkl","zxcvbnm","qazwsx","1q2w3e","1q2w3e4r",
        "zaq1zaq1","!@#$%^&*","pass","login","admin123","root","toor",
        "changeme","default","guest","user","test1","testing","test1234",
        "passw0rd","p@ssword","p@ss123","p@ssw0rd","p@$$word","pa$$word",
        "secret","secret1","secret123","hunter2","correct","horse","battery",
        "staple","password!","password@","password#","mypassword","mypass",
        "qwerty1","q1w2e3r4","abcd1234","abc1234","aaa111","aaaaaa","aaaaaaaa",
        "111222","112233","123321","321321","696969","777777","888888","999999",
        "101010","121212","131313","161616","202020","212121","232323","246810",
        "147258","258369","159357","753159","951753","abcabc","abcdef","abcdefg",
        "abcdefgh","password11","password12","password0","love","lovely",
        "loveyou","ilove","iloveu","lover","darling","sweetheart","sugar",
        "honey","babe","baby","cutie","sexy","sex123","fuck","fuckyou",
        "fuckoff","asshole","shit","bullshit","bastard","cunt","bitch",
        "dick","cock","pussy","naked","nude","naughty","horny","slut",
        "playboy","boobs","boob","nipple","porn","pornhub","xvideo","xnxx",
        "cricket","india","pakistan","bharat","mumbai","delhi","bangalore",
        "chennai","kolkata","hyderabad","pune","india123","india@123",
        "iloveindia","bharat123","jai hind","hindustani","namaste","namaste1",
        "ram123","shiva","hanuman","ganesh","lakshmi","saraswati","krishna",
        "vishnu","brahma","allah","jesus","jesus123","god","god123",
        "superman1","batman1","spiderman","ironman","thor","captain","avengers",
        "marvel","starwars","matrix","nintendo","playstation","xbox",
        "minecraft","fortnite","roblox","pubg","valorant","freef1re",
        "welcome1","welcome123","hello123","hello1","letmein1","newpass",
        "newpassword","oldpassword","mypassword1","temppass","temp1234"
    );
}
