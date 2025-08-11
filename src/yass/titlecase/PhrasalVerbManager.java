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

import org.apache.commons.io.input.ReaderInputStream;
import yass.YassProperties;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PhrasalVerbManager {

    public static Map<String, List<PhrasalVerb>> phrasalVerbs;
    private final YassProperties yassProperties;
    private static PhrasalVerbManager instance;
    private long lastUpdate;

    public PhrasalVerbManager(YassProperties props) {
        yassProperties = props;
        phrasalVerbs = init();
    }


    private Map<String, List<PhrasalVerb>> init() {
        InputStream inputStream = getClass().getResourceAsStream("/yass/resources/spell/phrasal_verbs.txt");
        Map<String, List<PhrasalVerb>> packagedDictionary = buildDictionaryMap(inputStream);
        Map<String, List<PhrasalVerb>> localDictionary;
        boolean hasChanges = false;
        File dictionary;
        if (yassProperties != null) {
            dictionary = new File(yassProperties.getUserDir() + File.separator + "phrasal_verbs.txt");
        } else {
            dictionary = null;
        }
        if (dictionary == null || !dictionary.exists()) {
            hasChanges = true;
            localDictionary = new HashMap<>();
        } else {
            localDictionary = readDictionaryFile(dictionary);
        }
        for (Map.Entry<String, List<PhrasalVerb>> entries : packagedDictionary.entrySet()) {
            if (localDictionary.containsKey(entries.getKey())) {
                List<PhrasalVerb> localList = localDictionary.get(entries.getKey());
                List<PhrasalVerb> packagedList = entries.getValue();
                for (PhrasalVerb verb : packagedList) {
                    if (!localList.contains(verb)) {
                        localList.add(verb);
                        hasChanges = true;
                    }
                }
            } else {
                hasChanges = true;
                localDictionary.put(entries.getKey(), entries.getValue());
            }
        }
        if (hasChanges && dictionary != null) {
            saveDictionary(dictionary, localDictionary);
        }
        return localDictionary;
    }

    public void reinit() {
        File dictionary = new File(yassProperties.getUserDir() + File.separator + "phrasal_verbs.txt");
        if (dictionary.lastModified() > lastUpdate) {
            phrasalVerbs = init();
        }
    }

    public Map<String, List<PhrasalVerb>> readDictionaryFile(File dictionary) {
        Map<String, List<PhrasalVerb>> returnMap;
        try {
            Reader reader = new FileReader(dictionary);
            InputStream inputStream = ReaderInputStream.builder()
                                                       .setReader(reader)
                                                       .setCharset(StandardCharsets.UTF_8)
                                                       .get();
            returnMap = buildDictionaryMap(inputStream);
            lastUpdate = dictionary.lastModified();
        } catch (IOException e) {
            return new HashMap<>();
        }
        return returnMap;
    }

    private Map<String, List<PhrasalVerb>> buildDictionaryMap(InputStream dictionaryReader) {
        Map<String, List<PhrasalVerb>> returnMap = new HashMap<>();
        BufferedReader inputStream;
        try {
            inputStream = new BufferedReader(new InputStreamReader(dictionaryReader, StandardCharsets.UTF_8));
            String line;
            while ((line = inputStream.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] entries = line.split(";");
                if (entries.length != 5) {
                    continue;
                }
                List<PhrasalVerb> phrasalVerbList = returnMap.computeIfAbsent(entries[4], k -> new ArrayList<>());
                phrasalVerbList.add(new PhrasalVerb(entries[0], entries[1], entries[2], entries[3], entries[4]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return returnMap;
    }

    public void saveDictionary(File dictionary, Map<String, List<PhrasalVerb>> dictionaryMap) {
        try (FileOutputStream outputStream = new FileOutputStream(dictionary)) {
            List<PhrasalVerb> verbs = dictionaryMap.values().stream().flatMap(Collection::stream)
                                                   .sorted(Comparator.comparing(PhrasalVerb::verb)).toList();
            for (PhrasalVerb verb : verbs) {
                String line = String.join(";", verb.verb(), verb.gerund(), verb.simplePast(), verb.pastParticible(),
                                          verb.advProp());
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean containsPhrasalVerb(String keyword, String expression) {
        List<PhrasalVerb> phrasalVerbList = phrasalVerbs.get(keyword);
        if (phrasalVerbList == null) {
            return false;
        }
        for (PhrasalVerb entry : phrasalVerbList) {
            if (entry.isPhrasalVerb(expression)) {
                return true;
            }
        }
        return false;
    }

    public static void setInstance(YassProperties props) {
        instance = new PhrasalVerbManager(props);
    }

    public static PhrasalVerbManager getInstance() {
        return instance;
    }
}
