// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.mojang.brigadier.tree;

import com.mojang.brigadier.AmbiguityConsumer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public abstract class CommandNode<S> implements Comparable<CommandNode<S>> {
    private final Map<String, CommandNode<S>> children = new LinkedHashMap<>();
    private final Map<String, ArgumentCommandNode<S, ?>> arguments = new LinkedHashMap<>();
    private final Predicate<S> requirement;
    private final BiPredicate<CommandContextBuilder<S>, ImmutableStringReader> contextRequirement;
    private final CommandNode<S> redirect;
    private final RedirectModifier<S> modifier;
    private final boolean forks;
    private Command<S> command;
    private boolean hasLiterals = false;

    protected CommandNode(final Command<S> command, final Predicate<S> requirement, final CommandNode<S> redirect, final RedirectModifier<S> modifier, final boolean forks) {
        this.command = command;
        this.requirement = requirement;
        this.contextRequirement = (context, reader) -> true;
        this.redirect = redirect;
        this.modifier = modifier;
        this.forks = forks;
    }

    protected CommandNode(final Command<S> command, final Predicate<S> requirement, final BiPredicate<CommandContextBuilder<S>, ImmutableStringReader> contextRequirement, final CommandNode<S> redirect, final RedirectModifier<S> modifier, final boolean forks) {
        this.command = command;
        this.requirement = requirement;
        this.contextRequirement = contextRequirement;
        this.redirect = redirect;
        this.modifier = modifier;
        this.forks = forks;
    }

    public Command<S> getCommand() {
        return command;
    }

    public Collection<CommandNode<S>> getChildren() {
        return children.values();
    }

    public CommandNode<S> getChild(final String name) {
        return children.get(name);
    }

    public CommandNode<S> getRedirect() {
        return redirect;
    }

    public RedirectModifier<S> getRedirectModifier() {
        return modifier;
    }

    public boolean canUse(final S source) {
        return requirement.test(source);
    }

    public boolean canUse(final CommandContextBuilder<S> context, final ImmutableStringReader reader) {
        return contextRequirement.test(context, reader);
    }

    public void addChild(final CommandNode<S> node) {
        if (node instanceof RootCommandNode) {
            throw new UnsupportedOperationException("Cannot add a RootCommandNode as a child to any other CommandNode");
        }

        final CommandNode<S> child = children.get(node.getName());
        if (child != null) {
            // We've found something to merge onto
            if (node.getCommand() != null) {
                child.command = node.getCommand();
            }
            for (final CommandNode<S> grandchild : node.getChildren()) {
                child.addChild(grandchild);
            }
        } else {
            children.put(node.getName(), node);
            if (node instanceof LiteralCommandNode) {
                hasLiterals = true;
            } else if (node instanceof ArgumentCommandNode) {
                arguments.put(node.getName(), (ArgumentCommandNode<S, ?>) node);
            }
        }
    }

    public void removeChildByName(final String name) {
        final CommandNode<S> child = children.remove(name);
        if (child != null) {
            arguments.remove(name);
        }
    }

    public void findAmbiguities(final AmbiguityConsumer<S> consumer) {
        Set<String> matches = new HashSet<>();

        for (final CommandNode<S> child : children.values()) {
            for (final CommandNode<S> sibling : children.values()) {
                if (child == sibling) {
                    continue;
                }

                for (final String input : child.getExamples()) {
                    if (sibling.isValidInput(input)) {
                        matches.add(input);
                    }
                }

                if (matches.size() > 0) {
                    consumer.ambiguous(this, child, sibling, matches);
                    matches = new HashSet<>();
                }
            }

            child.findAmbiguities(consumer);
        }
    }

    protected abstract boolean isValidInput(final String input);

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandNode)) return false;

        final CommandNode<S> that = (CommandNode<S>) o;

        if (!children.equals(that.children)) return false;
        if (command != null ? !command.equals(that.command) : that.command != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * children.hashCode() + (command != null ? command.hashCode() : 0);
    }

    public Predicate<S> getRequirement() {
        return requirement;
    }

    public BiPredicate<CommandContextBuilder<S>, ImmutableStringReader> getContextRequirement() {
        return contextRequirement;
    }

    public abstract String getName();

    public abstract String getUsageText();

    public abstract void parse(StringReader reader, CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException;

    public abstract CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) throws CommandSyntaxException;

    public abstract ArgumentBuilder<S, ?> createBuilder();

    protected abstract String getSortedKey();

    public Collection<? extends CommandNode<S>> getRelevantNodes(final StringReader input) {
        if (hasLiterals) {
            final int cursor = input.getCursor();
            while (input.canRead() && input.peek() != ' ') {
                input.skip();
            }
            final String text = input.getString().substring(cursor, input.getCursor());
            input.setCursor(cursor);
            final CommandNode<S> node = children.get(text);
            if (node instanceof LiteralCommandNode<?>) {
                final int argumentsCount = arguments.size();
                if (argumentsCount == 0) {
                    return Collections.singletonList(node);
                } else {
                    final List<CommandNode<S>> nodes =
                            new ArrayList<>(argumentsCount + 1);
                    nodes.add(node); // literals have priority over arguments
                    nodes.addAll(arguments.values());
                    return nodes;
                }
            } else {
                return arguments.values();
            }
        } else {
            return arguments.values();
        }
    }

    @Override
    public int compareTo(final CommandNode<S> o) {
        if (this instanceof LiteralCommandNode == o instanceof LiteralCommandNode) {
            return getSortedKey().compareTo(o.getSortedKey());
        }

        return (o instanceof LiteralCommandNode) ? 1 : -1;
    }

    public boolean isFork() {
        return forks;
    }

    public abstract Collection<String> getExamples();
}
