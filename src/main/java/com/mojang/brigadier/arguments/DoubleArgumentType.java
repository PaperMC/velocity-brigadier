package com.mojang.brigadier.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.ParameterizedCommandExceptionType;

public class DoubleArgumentType implements ArgumentType<Double> {
    public static final ParameterizedCommandExceptionType ERROR_TOO_SMALL = new ParameterizedCommandExceptionType("argument.double.low", "Double must not be less than ${minimum}, found ${found}", "found", "minimum");
    public static final ParameterizedCommandExceptionType ERROR_TOO_BIG = new ParameterizedCommandExceptionType("argument.double.big", "Double must not be more than ${maximum}, found ${found}", "found", "maximum");

    private final double minimum;
    private final double maximum;

    private DoubleArgumentType(final double minimum, final double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static DoubleArgumentType doubleArg() {
        return doubleArg(-Double.MAX_VALUE);
    }

    public static DoubleArgumentType doubleArg(final double min) {
        return doubleArg(min, Double.MAX_VALUE);
    }

    public static DoubleArgumentType doubleArg(final double min, final double max) {
        return new DoubleArgumentType(min, max);
    }

    public static double getDouble(final CommandContext<?> context, final String name) {
        return context.getArgument(name, Double.class);
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    @Override
    public <S> Double parse(final StringReader reader, final CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException {
        final int start = reader.getCursor();
        final double result = reader.readDouble();
        if (result < minimum) {
            reader.setCursor(start);
            throw ERROR_TOO_SMALL.createWithContext(reader, result, minimum);
        }
        if (result > maximum) {
            reader.setCursor(start);
            throw ERROR_TOO_BIG.createWithContext(reader, result, maximum);
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleArgumentType)) return false;

        final DoubleArgumentType that = (DoubleArgumentType) o;
        return maximum == that.maximum && minimum == that.minimum;
    }

    @Override
    public int hashCode() {
        return (int) (31 * minimum + maximum);
    }

    @Override
    public String toString() {
        if (minimum == -Double.MAX_VALUE && maximum == Double.MAX_VALUE) {
            return "double()";
        } else if (maximum == Double.MAX_VALUE) {
            return "double(" + minimum + ")";
        } else {
            return "double(" + minimum + ", " + maximum + ")";
        }
    }
}