package dk.via.partay.app.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 6-character join codes without the look-alike characters 0, O, 1 and I. */
@Component
public class JoinCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
