package org.acspl.intellij;

import org.acspl.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import org.junit.Test;
import java.util.List;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;
import static org.junit.Assert.*;

public class AcsplLexerTest {
    private static IElementType type(String text) { return TokenScan.scan(text).getFirst().type(); }
    @Test public void caseAndPostfixIndexing() {
        assertSame(COMMAND, type("pTp")); assertSame(FUNCTION, type("sIn"));
        assertSame(VARIABLE, type("FPOS0")); assertSame(IDENTIFIER, type("Fpos0"));
        assertSame(VARIABLE, type("I99")); assertSame(VARIABLE, type("V0"));
        assertSame(IDENTIFIER, type("first_axis"));
        assertSame(IDENTIFIER, type("_axis"));
        assertSame(IDENTIFIER, type("AUTOEXEC"));
        assertSame(FUNCTION, TokenScan.scan("LCI.PowerPWMOut").getLast().type());
        assertSame(VARIABLE, TokenScan.scan("MotionDuration.Distance").getLast().type());
    }
    @Test public void literalsAndComments() {
        var tokens = TokenScan.scan("0xFF 0b111 1.25e-3 .5 1e9 '!' \"!\\n\" ! 中文注释\r\nINT n");
        assertEquals(5, tokens.stream().filter(t -> t.type() == NUMBER).count());
        assertEquals(1, tokens.stream().filter(t -> t.type() == CHARACTER).count());
        assertEquals(1, tokens.stream().filter(t -> t.type() == STRING).count());
        assertEquals(1, tokens.stream().filter(t -> t.type() == COMMENT).count());
        assertEquals("! 中文注释", tokens.stream().filter(t -> t.type() == COMMENT).findFirst().orElseThrow().text());
        assertSame(KEYWORD, tokens.stream().filter(t -> t.text().equals("INT")).findFirst().orElseThrow().type());
    }
    @Test public void switchesAreNotDivisionAndLeaveAxisNumber() {
        var tokens = TokenScan.scan("PTP/rw0,100; x = numerator/r; PTP /e (0,1), 2, 3");
        assertEquals(List.of("/rw", "/e"), tokens.stream().filter(t -> t.type() == SWITCH).map(TokenScan.Token::text).toList());
        assertEquals(1, tokens.stream().filter(t -> t.type() == OPERATOR && t.text().equals("/")).count());
        assertTrue(tokens.stream().anyMatch(t -> t.type() == NUMBER && t.text().equals("0")));
    }
    @Test public void bitSelectionDoesNotBecomeDecimal() {
        var tokens = TokenScan.scan("IN0.4; MST(0).#MOVE; x = 0.4");
        assertEquals(2, tokens.stream().filter(t -> t.type() == DOT).count());
        assertTrue(tokens.stream().anyMatch(t -> t.type() == NUMBER && t.text().equals("4")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == NUMBER && t.text().equals("0.4")));
    }
    @Test public void unfinishedStringRecoversAtNewline() {
        var tokens = TokenScan.scan("DISP \"unfinished ! text\nPTP/e 0,100");
        assertEquals(1, tokens.stream().filter(t -> t.type() == STRING).count());
        assertTrue(tokens.stream().anyMatch(t -> t.type() == COMMAND && t.text().equals("PTP")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == SWITCH));
    }
    @Test public void canRestartAtEveryTokenIncludingWhitespace() {
        String text = "PTP /rw0,100\r\nDISP \"x!\"; TILL ^MST(0).#MOVE\nIN0 . 4 = 0x10 / SIN(.5)\nLCI.PowerPWMOut(0); MotionDuration . Distance\n";
        var expected = TokenScan.scan(text);
        for (int i = 0; i < expected.size(); i++) {
            var token = expected.get(i);
            var lexer = new AcsplLexer();
            lexer.start(text, token.start(), text.length(), token.state());
            for (int j = i; j < expected.size(); j++) {
                assertSame("restart at " + token.start(), expected.get(j).type(), lexer.getTokenType());
                assertEquals(expected.get(j).start(), lexer.getTokenStart());
                assertEquals(expected.get(j).end(), lexer.getTokenEnd());
                assertEquals(expected.get(j).state(), lexer.getState());
                lexer.advance();
            }
            assertNull(lexer.getTokenType());
        }
    }
    @Test public void restartAfterEditMatchesFullLexing() {
        String original = "PTP /r0, 100\nIN0.4 = 1";
        var token = TokenScan.scan(original).stream().filter(t -> t.type() == SWITCH).findFirst().orElseThrow();
        String edited = original.replace("/r0", "/rw0");
        var lexer = new AcsplLexer();
        lexer.start(edited, token.start(), edited.length(), token.state());
        var expected = TokenScan.scan(edited).stream().filter(t -> t.start() >= token.start()).toList();
        for (var next : expected) {
            assertSame(next.type(), lexer.getTokenType()); assertEquals(next.end(), lexer.getTokenEnd()); lexer.advance();
        }
        assertNull(lexer.getTokenType());
    }
}
