package kelp;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance's server list: the servers.dat Minecraft keeps (Multiplayer's list). Kelp reads it to show the servers,
 * and adds or removes one by changing just that entry, so everything else Minecraft keeps there (icons, settings)
 * stays as it was.
 *
 * Minecraft keeps a second, hidden list in the same file (servers joined with Direct Connect, or straight from Kelp),
 * marked "hidden"; those aren't shown, just like in Minecraft's Multiplayer screen.
 */
public final class Servers {
    /** A server: the name it's shown as, its address, and where it is in the file. */
    public record Server(String name, String address, int index) {
    }

    private Servers() {
    }

    public static Path file(Instance instance) {
        return instance.folder().resolve("servers.dat");
    }

    /**
     * Whether an address is a plain one Kelp can hand to the game: a name or numbers with dots and dashes, maybe a
     * :port (up to 65535), or an IPv6 address in [brackets]. Nothing else, so nothing odd can sneak into the game's
     * command line.
     */
    public static boolean validAddress(String address) {
        if (address == null || address.startsWith("-") || address.length() > 253) return false;
        if (!address.matches("[A-Za-z0-9._\\-]+(:[0-9]{1,5})?|\\[[0-9A-Fa-f:.]+](:[0-9]{1,5})?")) return false;
        int colon = address.lastIndexOf(':');
        boolean hasPort = colon > 0 && (address.charAt(0) != '[' || address.lastIndexOf(']') < colon);
        return !hasPort || Integer.parseInt(address.substring(colon + 1)) <= 65535;
    }

    /** The servers in the list, in its order (not the hidden ones). Empty if there's no list yet, or it can't be read. */
    public static List<Server> list(Path file) {
        List<Server> servers = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file)) return servers;
            List<Object> entries = entries(Nbt.read(Files.readAllBytes(file))).items();
            for (int i = 0; i < entries.size(); i++) {
                if (!(entries.get(i) instanceof Map<?, ?> server) || hidden(server)) continue;
                Object ip = server.get("ip");
                Object name = server.get("name");
                if (ip instanceof String address) servers.add(new Server(name instanceof String n && !n.isBlank() ? n : address, address, i));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't read the server list " + file + ": " + e.getMessage());
        }
        return servers;
    }

    private static boolean hidden(Map<?, ?> server) {
        return server.get("hidden") instanceof Byte b && b != 0;
    }

    /**
     * Adds a server at the end of the list (making the list if there isn't one). One that's in the hidden list
     * already is brought out of it instead, like Minecraft's own Add Server does.
     */
    @SuppressWarnings("unchecked")
    public static void add(Path file, String name, String address) throws IOException {
        if (!validAddress(address)) throw new IOException("That doesn't look like a server address.");
        Map<String, Object> root = Files.isRegularFile(file) ? read(file) : new LinkedHashMap<>();
        String shown = name.isBlank() ? address : name.strip();
        List<Object> entries = entries(root).items();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof Map<?, ?> server && hidden(server) && address.equals(server.get("ip"))) {
                Map<String, Object> found = (Map<String, Object>) server;
                entries.remove(i);
                found.put("hidden", (byte) 0);
                found.put("name", shown);
                entries.add(firstHidden(entries), found); // at the end of the shown ones
                save(file, root);
                return;
            }
        }
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("ip", address);
        server.put("name", shown);
        entries.add(firstHidden(entries), server);
        save(file, root);
    }

    /** Where the hidden ones start (Minecraft keeps them after the shown ones). */
    private static int firstHidden(List<Object> entries) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i) instanceof Map<?, ?> m && hidden(m)) return i;
        return entries.size();
    }

    /**
     * Removes a server, checking it's still the same one at that place (Minecraft may have changed the list since it
     * was shown). False if it wasn't there anymore, so nothing was removed.
     */
    public static boolean remove(Path file, Server server) throws IOException {
        Map<String, Object> root = read(file);
        List<Object> entries = entries(root).items();
        int index = server.index();
        if (index < 0 || index >= entries.size() || !(entries.get(index) instanceof Map<?, ?> found)
                || !server.address().equals(found.get("ip"))) {
            return false;
        }
        entries.remove(index);
        save(file, root);
        return true;
    }

    private static Map<String, Object> read(Path file) throws IOException {
        try {
            return Nbt.read(Files.readAllBytes(file));
        } catch (java.io.EOFException e) {
            throw new IOException("the server list is damaged (it ends too soon)");
        }
    }

    /** The "servers" list in the file, made if it isn't there. */
    private static Nbt.ListTag entries(Map<String, Object> root) throws IOException {
        if (root.get("servers") instanceof Nbt.ListTag list) {
            if (list.type() != 10 && !list.items().isEmpty()) throw new IOException("the server list isn't a list of servers");
            return list;
        }
        Nbt.ListTag list = new Nbt.ListTag(10, new ArrayList<>());
        root.put("servers", list);
        return list;
    }

    /** Written next to it, then swapped in, so a crash halfway can't leave a broken list. */
    private static void save(Path file, Map<String, Object> root) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path next = file.resolveSibling(file.getFileName() + ".kelp-new");
        Files.write(next, Nbt.write(root));
        try {
            Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(next, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
