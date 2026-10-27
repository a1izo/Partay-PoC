package dk.via.partay.app.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinCodeGeneratorTest {

    private final JoinCodeGenerator generator = new JoinCodeGenerator();

    @Test
    void codeHasSixCharacters() {
        assertEquals(6, generator.next().length());
    }

    @Test
    void onlyAllowedCharacters() {
        for (int i = 0; i < 1000; i++) {
            for (char c : generator.next().toCharArray()) {
                assertTrue(JoinCodeGenerator.ALPHABET.indexOf(c) >= 0, "unexpected character " + c);
            }
        }
    }

    @Test
    void thousandCodesHaveNoLookAlikes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.next());
        }
        for (String code : codes) {
            assertFalse(code.matches(".*[0O1I].*"), code);
        }
        // 32^6 possible codes: 1000 random ones should practically never collide much
        assertTrue(codes.size() > 990);
    }
}
