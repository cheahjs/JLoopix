package me.jscheah.sphinx;

import me.jscheah.sphinx.params.GroupECC;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupECCTest {
    @Test
    void testExponCommutative() {
        GroupECC G = new GroupECC();
        BigInteger sec1 = G.generateSecret();
        BigInteger sec2 = G.generateSecret();
        ECPoint gen = G.Generator;

        assertTrue(G.expon(G.expon(gen, sec1), sec2).equals(G.expon(G.expon(gen, sec2), sec1)));
    }

    @Test
    void testExponEqualsMultiExpon() {
        GroupECC G = new GroupECC();
        BigInteger sec1 = G.generateSecret();
        BigInteger sec2 = G.generateSecret();
        ECPoint gen = G.Generator;

        assertTrue(G.expon(G.expon(gen, sec1), sec2).equals(G.multiExpon(gen, Arrays.asList(sec2, sec1))));
    }

    @Test
    void testExponInGroup() {
        GroupECC G = new GroupECC();
        BigInteger sec1 = G.generateSecret();
        ECPoint gen = G.Generator;

        assertTrue(G.inGroup(G.expon(gen, sec1)));
    }

    @Test
    void testPrintable() {
        GroupECC G = new GroupECC();

        assertEquals(G.printableString(G.Generator), "04b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21bd376388b5f723fb4c22dfe6cd4375a05a07476444d5819985007e34");
    }
}