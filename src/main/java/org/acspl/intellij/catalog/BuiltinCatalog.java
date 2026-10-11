package org.acspl.intellij.catalog;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable catalog shared by the lexer, completion and documentation. */
public final class BuiltinCatalog {
    public record Source(String manual, String section, int page) {}
    public record Switch(String name, String description) {}
    public record Entry(String name, String kind, List<String> signatures, String description,
                        List<String> parameters, List<Switch> switches, List<Source> sources,
                        boolean array, List<String> owners) {
        public boolean caseSensitive() { return kind.equals("variable") || kind.equals("constant") || kind.equals("type") || kind.equals("label"); }
        public boolean function() { return kind.equals("function"); }
        public String signature() { return signatures.isEmpty() ? name : signatures.getFirst(); }
    }
    private record Root(String languageVersion, List<Entry> entries) {}
    private static final class Holder { private static final BuiltinCatalog INSTANCE = new BuiltinCatalog(); }
    private final List<Entry> entries;
    private final Map<String, Entry> exact = new LinkedHashMap<>();
    private final Map<String, Entry> insensitive = new HashMap<>();
    private final List<Entry> arrays;
    private final List<Entry> commands;
    private final Map<String, Integer> commandIds = new HashMap<>();
    private final List<String> namespaces;

    private BuiltinCatalog() {
        var stream = BuiltinCatalog.class.getResourceAsStream("/catalog/acspl-3.12.json");
        if (stream == null) throw new IllegalStateException("ACSPL+ catalog is missing");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            entries = List.copyOf(new Gson().fromJson(reader, Root.class).entries());
        } catch (Exception e) { throw new IllegalStateException("Cannot read ACSPL+ catalog", e); }
        for (Entry entry : entries) {
            exact.putIfAbsent(entry.name(), entry);
            if (!entry.caseSensitive()) insensitive.putIfAbsent(entry.name().toUpperCase(Locale.ROOT), entry);
        }
        arrays = entries.stream().filter(Entry::array)
            .sorted(Comparator.comparingInt((Entry e) -> e.name().length()).reversed()).toList();
        commands = entries.stream().filter(e -> e.kind().equals("command")).toList();
        namespaces = entries.stream().filter(e -> e.kind().equals("type")).map(Entry::name).toList();
        for (int i = 0; i < commands.size(); i++) commandIds.put(commands.get(i).name().toUpperCase(Locale.ROOT), i + 1);
    }
    public static BuiltinCatalog getInstance() { return Holder.INSTANCE; }
    public List<Entry> entries() { return entries; }
    public Entry find(String text) {
        Entry entry = exact.get(text);
        if (entry != null) return entry;
        entry = insensitive.get(text.toUpperCase(Locale.ROOT));
        if (entry != null) return entry;
        for (Entry array : arrays) {
            if (text.startsWith(array.name()) && text.length() > array.name().length()
                && text.substring(array.name().length()).chars().allMatch(c -> c >= '0' && c <= '9')) return array;
        }
        return null;
    }
    public int commandId(String text) { return commandIds.getOrDefault(text.toUpperCase(Locale.ROOT), 0); }
    public Entry command(int id) { return id > 0 && id <= commands.size() ? commands.get(id - 1) : null; }
    public int namespaceId(String name) { return namespaces.indexOf(name) + 1; }
    public String namespace(int id) { return id > 0 && id <= namespaces.size() ? namespaces.get(id - 1) : null; }
}
