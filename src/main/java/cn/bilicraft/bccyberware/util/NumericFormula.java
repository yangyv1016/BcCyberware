package cn.bilicraft.bccyberware.util;

/**
 * Small, deterministic arithmetic evaluator used by capacity value sources.
 * It intentionally supports only numbers, the variable {@code value}, parentheses and + - * /.
 */
public final class NumericFormula {
    private final String expression;
    private int position;
    private double value;

    private NumericFormula(String expression, double value) {
        this.expression = expression;
        this.value = value;
    }

    public static double evaluate(String expression, double value) {
        if (expression == null || expression.isBlank()) {
            return value;
        }
        NumericFormula parser = new NumericFormula(expression, value);
        double result = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.position != parser.expression.length()) {
            throw new IllegalArgumentException("公式在位置 " + parser.position + " 附近包含无法识别的内容");
        }
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("公式结果不是有限数值");
        }
        return result;
    }

    private double parseExpression() {
        double result = parseTerm();
        while (true) {
            skipWhitespace();
            if (match('+')) {
                result += parseTerm();
            } else if (match('-')) {
                result -= parseTerm();
            } else {
                return result;
            }
        }
    }

    private double parseTerm() {
        double result = parseUnary();
        while (true) {
            skipWhitespace();
            if (match('*')) {
                result *= parseUnary();
            } else if (match('/')) {
                double divisor = parseUnary();
                if (divisor == 0.0) {
                    throw new IllegalArgumentException("公式不能除以 0");
                }
                result /= divisor;
            } else {
                return result;
            }
        }
    }

    private double parseUnary() {
        skipWhitespace();
        if (match('+')) {
            return parseUnary();
        }
        if (match('-')) {
            return -parseUnary();
        }
        return parsePrimary();
    }

    private double parsePrimary() {
        skipWhitespace();
        if (match('(')) {
            double result = parseExpression();
            skipWhitespace();
            if (!match(')')) {
                throw new IllegalArgumentException("公式缺少右括号");
            }
            return result;
        }
        if (peekWord("value")) {
            position += 5;
            return value;
        }
        int start = position;
        boolean dotSeen = false;
        while (position < expression.length()) {
            char current = expression.charAt(position);
            if (Character.isDigit(current)) {
                position++;
            } else if (current == '.' && !dotSeen) {
                dotSeen = true;
                position++;
            } else {
                break;
            }
        }
        if (start == position) {
            throw new IllegalArgumentException("公式在位置 " + position + " 需要数字、value 或左括号");
        }
        return Double.parseDouble(expression.substring(start, position));
    }

    private boolean peekWord(String word) {
        return expression.regionMatches(true, position, word, 0, word.length())
                && (position + word.length() == expression.length()
                || !Character.isLetterOrDigit(expression.charAt(position + word.length())));
    }

    private boolean match(char expected) {
        if (position < expression.length() && expression.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
            position++;
        }
    }
}

