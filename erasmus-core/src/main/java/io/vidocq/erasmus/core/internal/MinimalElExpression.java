/*
 * Copyright (c) 2026 Yann Blazart, Antoine Sabot-Durand and the Vidocq contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * or any later version, which is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * It is also made available under the European Union Public Licence v. 1.2,
 * which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * SPDX-License-Identifier: EPL-2.0 OR EUPL-1.2 OR GPL-2.0-or-later
 */
package io.vidocq.erasmus.core.internal;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * A deliberately minimal subset of Unified EL, evaluated over a fixed variable map — enough
 * to cover the message-template expressions Bean Validation actually needs (constraint
 * attribute references, {@code validatedValue}, simple comparisons/arithmetic, and the
 * ternary operator real-world messages use for pluralization), and nothing more. No method
 * calls, no bracket/dot navigation into nested objects, no user-defined EL functions — this
 * is not a general-purpose EL engine, and isn't trying to be one. Precedence, low to high:
 * ternary, {@code ||}, {@code &&}, equality, relational, additive, multiplicative, unary.
 */
final class MinimalElExpression {

    private final String source;
    private final Map<String, Object> variables;
    private int pos;

    private MinimalElExpression(String source, Map<String, Object> variables) {
        this.source = source;
        this.variables = variables;
        this.pos = 0;
    }

    static Object evaluate(String expression, Map<String, Object> variables) {
        MinimalElExpression parser = new MinimalElExpression(expression, variables);
        Object value = parser.parseTernary();
        parser.skipWhitespace();
        if (parser.pos != expression.length()) {
            throw new IllegalArgumentException(
                    "Unexpected trailing input in EL expression '" + expression + "' at position " + parser.pos);
        }
        return value;
    }

    private Object parseTernary() {
        Object condition = parseOr();
        skipWhitespace();
        if (peek('?')) {
            pos++;
            Object whenTrue = parseTernary();
            skipWhitespace();
            expect(':');
            Object whenFalse = parseTernary();
            return asBoolean(condition) ? whenTrue : whenFalse;
        }
        return condition;
    }

    private Object parseOr() {
        Object left = parseAnd();
        skipWhitespace();
        while (peekAhead("||")) {
            pos += 2;
            Object right = parseAnd();
            left = asBoolean(left) || asBoolean(right);
            skipWhitespace();
        }
        return left;
    }

    private Object parseAnd() {
        Object left = parseEquality();
        skipWhitespace();
        while (peekAhead("&&")) {
            pos += 2;
            Object right = parseEquality();
            left = asBoolean(left) && asBoolean(right);
            skipWhitespace();
        }
        return left;
    }

    private Object parseEquality() {
        Object left = parseRelational();
        skipWhitespace();
        while (true) {
            if (peekAhead("==")) {
                pos += 2;
                left = Objects.equals(left, parseRelational());
            } else if (peekAhead("!=")) {
                pos += 2;
                left = !Objects.equals(left, parseRelational());
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private Object parseRelational() {
        Object left = parseAdditive();
        skipWhitespace();
        while (true) {
            if (peekAhead(">=")) {
                pos += 2;
                left = compare(left, parseAdditive()) >= 0;
            } else if (peekAhead("<=")) {
                pos += 2;
                left = compare(left, parseAdditive()) <= 0;
            } else if (peek('>')) {
                pos++;
                left = compare(left, parseAdditive()) > 0;
            } else if (peek('<')) {
                pos++;
                left = compare(left, parseAdditive()) < 0;
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private Object parseAdditive() {
        Object left = parseMultiplicative();
        skipWhitespace();
        while (true) {
            if (peek('+')) {
                pos++;
                left = arithmetic(left, parseMultiplicative(), '+');
            } else if (peek('-')) {
                pos++;
                left = arithmetic(left, parseMultiplicative(), '-');
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private Object parseMultiplicative() {
        Object left = parseUnary();
        skipWhitespace();
        while (true) {
            if (peek('*')) {
                pos++;
                left = arithmetic(left, parseUnary(), '*');
            } else if (peek('/')) {
                pos++;
                left = arithmetic(left, parseUnary(), '/');
            } else if (peek('%')) {
                pos++;
                left = arithmetic(left, parseUnary(), '%');
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private Object parseUnary() {
        skipWhitespace();
        if (peek('!')) {
            pos++;
            return !asBoolean(parseUnary());
        }
        if (peek('-')) {
            pos++;
            return negate(parseUnary());
        }
        return parsePrimary();
    }

    private Object parsePrimary() {
        skipWhitespace();
        if (pos >= source.length()) {
            throw new IllegalArgumentException("Unexpected end of EL expression: " + source);
        }
        char c = source.charAt(pos);
        if (c == '(') {
            pos++;
            Object value = parseTernary();
            skipWhitespace();
            expect(')');
            return value;
        }
        if (c == '\'' || c == '"') {
            return parseStringLiteral(c);
        }
        if (Character.isDigit(c)) {
            return parseNumber();
        }
        if (Character.isJavaIdentifierStart(c)) {
            return parseIdentifierOrKeyword();
        }
        throw new IllegalArgumentException("Unexpected character '" + c + "' in EL expression: " + source);
    }

    private String parseStringLiteral(char quote) {
        pos++; // opening quote
        StringBuilder value = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != quote) {
            value.append(source.charAt(pos));
            pos++;
        }
        expect(quote);
        return value.toString();
    }

    private Object parseNumber() {
        int start = pos;
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
            pos++;
        }
        String literal = source.substring(start, pos);
        return literal.contains(".") ? (Object) Double.parseDouble(literal) : (Object) Long.parseLong(literal);
    }

    private Object parseIdentifierOrKeyword() {
        int start = pos;
        while (pos < source.length() && Character.isJavaIdentifierPart(source.charAt(pos))) {
            pos++;
        }
        String identifier = source.substring(start, pos);
        return switch (identifier) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "null" -> null;
            default -> variables.get(identifier);
        };
    }

    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char expected) {
        return pos < source.length() && source.charAt(pos) == expected;
    }

    private boolean peekAhead(String expected) {
        return source.regionMatches(pos, expected, 0, expected.length());
    }

    private void expect(char expected) {
        if (!peek(expected)) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' at position " + pos + " in EL expression: " + source);
        }
        pos++;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Expected a boolean in EL expression, got: " + value);
    }

    @SuppressWarnings("unchecked")
    private static int compare(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return toBigDecimal(leftNumber).compareTo(toBigDecimal(rightNumber));
        }
        if (left instanceof Comparable<?> && right != null && left.getClass().isInstance(right)) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        throw new IllegalArgumentException("Cannot compare " + left + " and " + right + " in EL expression");
    }

    private static Object arithmetic(Object left, Object right, char operator) {
        if (!(left instanceof Number leftNumber) || !(right instanceof Number rightNumber)) {
            throw new IllegalArgumentException("Arithmetic operator '" + operator + "' needs two numbers, got: "
                    + left + ", " + right);
        }
        BigDecimal a = toBigDecimal(leftNumber);
        BigDecimal b = toBigDecimal(rightNumber);
        boolean bothIntegral = !(leftNumber instanceof Double) && !(rightNumber instanceof Double);
        BigDecimal result = switch (operator) {
            case '+' -> a.add(b);
            case '-' -> a.subtract(b);
            case '*' -> a.multiply(b);
            case '/' -> a.divide(b, 16, java.math.RoundingMode.HALF_UP);
            case '%' -> a.remainder(b);
            default -> throw new IllegalArgumentException("Unknown arithmetic operator: " + operator);
        };
        return bothIntegral && operator != '/' ? (Object) result.longValueExact() : (Object) result.doubleValue();
    }

    private static Object negate(Object value) {
        if (value instanceof Long l) {
            return -l;
        }
        if (value instanceof Double d) {
            return -d;
        }
        throw new IllegalArgumentException("Cannot negate non-numeric value in EL expression: " + value);
    }

    private static BigDecimal toBigDecimal(Number number) {
        return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
    }
}
