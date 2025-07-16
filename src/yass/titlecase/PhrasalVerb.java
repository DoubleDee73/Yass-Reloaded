/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.titlecase;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record PhrasalVerb(String verb, String gerund, String simplePast, String pastParticible, String advProp) {
    public PhrasalVerb(String verb, String gerund, String simplePast, String pastParticible, String advProp) {
        this.verb = verb;
        boolean hasSomebody = verb.endsWith(" <sb>");
        if (StringUtils.isEmpty(gerund)) {
            this.gerund = verb.replace(" <sb>", "") + "ing" + (hasSomebody ? " <sb>" : "");
        } else {
            this.gerund = gerund + (hasSomebody && !gerund.endsWith(" <sb>") ? " <sb>": "");
        }
        if (StringUtils.isEmpty(simplePast)) {
            this.simplePast = buildPast(verb);
        } else {
            this.simplePast = simplePast;
        }
        if (StringUtils.isEmpty(pastParticible)) {
            this.pastParticible = buildPast(verb);
        } else {
            this.pastParticible = simplePast;
        }
        this.advProp = advProp;
    }

    private String buildPast(String verb) {
        boolean hasSomebody = verb.endsWith(" <sb>");
        verb = verb.replace(" <sb>", "");
        String temp;
        if (verb.endsWith("y")) {
            temp = verb.substring(0, verb.length() - 1) + "ied";
        } else if (verb.endsWith("e")) {
            temp = verb.substring(0, verb.length() - 1) + "d";
        } else {
            temp = verb + "ed";
        }
        return temp + (hasSomebody ? " <sb>" : "");
    }

    public boolean isPhrasalVerb(String expression) {
        return listExpressions().stream()
                                .anyMatch(it -> expression.equals(it + " " + advProp));
    }

    public Set<String> listExpressions() {
        return Stream.of(verb, thirdPerson(verb), gerund, gerund.replace("ing", "in'"), gerund.replace("ing", "in’"),
                         simplePast, pastParticible).collect(
                Collectors.toSet());
    }
    
    public String thirdPerson(String verb) {
        String temp;
        boolean hasSomebody = verb.endsWith(" <sb>");
        verb = verb.replace(" <sb>", "");
        if (verb.endsWith("s")) {
            temp = verb + "es";
        } else {
            temp = verb + "s";
        }
        return temp + (hasSomebody ? " <sb>" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PhrasalVerb other) {
            return this.verb.equals(other.verb) &&
                   this.advProp.equals(other.advProp);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return verb.hashCode() + advProp.hashCode();
    }
}
