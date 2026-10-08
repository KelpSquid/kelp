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
 */
public final class Servers {
    /** A server: the name it's shown as, and its address (like play.example.com or 192.168.1.5:25565). */
    public record Server(String name, String address) {
    }

    private Servers() {
    }

    public static Path file(Instance instance) {
        return instance.folder().resolve("servers.dat");
    }

    /**
     * Whether an address is a plain one Kelp can hand to the game: a name or numbers with dots and dashes, maybe a
     * :port, or an IPv6 address in [brackets]. Nothing else, so nothing odd can sneak into the game's command line.
     */
    public static boolean validAddress(String address) {
        return address != null && !address.startsWith("-") && address.length() <= 253
                && address.matches("[A-Za-z0-9._\\-]+(:[0-9]{1,5})?|\\[[0-9A-Fa-f:.]+](:[0-9]{1,5})?");
    }

    /** The servers in the list, in its order. Empty if there's no list yet (or it can't be read). */
    public static List<Server> list(Path file) {
        List<Server> servers = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file)) return servers;
            for (Object entry : entries(Nbt.read(Files.readAllBytes(file))).items()) {
                if (!(entry instanceof Map<?, ?> server)) continue;
                Object ip = server.get("ip");
                Object name = server.get("name");
                if (ip instanceof String address) servers.add(new Server(name instanceof String n ? n : address, address));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't read the server list " + file + ": " + e.getMessage());
        }
        return servers;
    }

    /** Adds a server at the end of the list (making the list if there isn't one). */
    public static void add(Path file, String name, String address) throws IOException {
        if (!validAddress(address)) throw new IOException("That doesn't look like a server address.");
        Map<String, Object> root = Files.isRegularFile(file) ? Nbt.read(Files.readAllBytes(file)) : new LinkedHashMap<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("ip", address);
        server.put("name", name.isBlank() ? address : name.strip());
        entries(root).items().add(server);
        save(file, root);
    }

    /** Removes the server at this place in the list. */
    public static void remove(Path file, int index) throws IOException {
        Map<String, Object> root = Nbt.read(Files.readAllBytes(file));
        Nbt.ListTag list = entries(root);
        if (index < 0 || index >= list.items().size()) return;
        list.items().remove(index);
        save(file, root);
    }

    /** The "servers" list in the file, made if it isn't there. */
    private static Nbt.ListTag entries(Map<String, Object> root) {
        if (root.get("servers") instanceof Nbt.ListTag list) return list;
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
