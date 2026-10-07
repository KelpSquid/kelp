package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Every account added to Kelp, and which one you're playing as, saved in accounts.properties.
 * That file holds sign-in keys, so Kelp keeps it in its own folder and never puts it anywhere else.
 */
public final class Accounts {
    private static final Path FILE = Folders.home().resolve("accounts.properties");
    private static List<Account> accounts;
    private static String activeId;

    private Accounts() {
    }

    /** Every account, in the order they were added. */
    public static synchronized List<Account> all() {
        load();
        return List.copyOf(accounts);
    }

    /** The account you're playing as. There's always one: an offline "Player" if nothing was added yet. */
    public static synchronized Account active() {
        load();
        for (Account account : accounts) {
            if (account.id().equals(activeId)) return account;
        }
        if (accounts.isEmpty()) add(Account.offline("Player"));
        activeId = accounts.get(0).id();
        return accounts.get(0);
    }

    public static synchronized void setActive(Account account) {
        load();
        activeId = account.id();
        save();
    }

    /** Adds an account, or updates it if it's already here (like after signing in again). It becomes the active one. */
    public static synchronized void add(Account account) {
        load();
        boolean replaced = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id().equals(account.id())) {
                accounts.set(i, account);
                replaced = true;
            }
        }
        if (!replaced) accounts.add(account);
        activeId = account.id();
        save();
    }

    /** Saves new tokens for an account without making it the active one. */
    public static synchronized void update(Account account) {
        load();
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id().equals(account.id())) accounts.set(i, account);
        }
        save();
    }

    public static synchronized void remove(Account account) {
        load();
        accounts.removeIf(a -> a.id().equals(account.id()));
        if (account.id().equals(activeId)) activeId = accounts.isEmpty() ? null : accounts.get(0).id();
        save();
    }

    /**
     * The active account, signed in again first if its Minecraft token ran out (they last a day).
     * Without internet the game still starts with the old token: single player works, servers don't.
     */
    public static Account readyToPlay() throws IOException, InterruptedException {
        Account account = active();
        if (!account.microsoft() || account.signedIn(System.currentTimeMillis()) || !MicrosoftLogin.ready()) return account;
        try {
            Account fresh = new MicrosoftLogin().refresh(account);
            update(fresh);
            return fresh;
        } catch (MicrosoftLogin.SignInException e) {
            if (e.signInAgain) throw e;
            return account;
        }
    }

    /** Whether any account is a Microsoft one, which proves someone here owns Minecraft. */
    public static synchronized boolean hasMicrosoft() {
        load();
        return accounts.stream().anyMatch(Account::microsoft);
    }

    private static void load() {
        if (accounts != null) return;
        accounts = new ArrayList<>();
        Properties values = new Properties();
        if (Files.exists(FILE)) {
            try (Reader in = Files.newBufferedReader(FILE)) {
                values.load(in);
            } catch (IOException e) {
                System.err.println("Couldn't read " + FILE + ": " + e.getMessage());
            }
            for (int i = 0; values.getProperty(i + ".id") != null; i++) {
                String p = i + ".";
                long expiresAt;
                try {
                    expiresAt = Long.parseLong(values.getProperty(p + "expiresAt", "0"));
                } catch (NumberFormatException e) {
                    expiresAt = 0;
                }
                accounts.add(new Account(values.getProperty(p + "id"), values.getProperty(p + "name", "Player"),
                        "microsoft".equals(values.getProperty(p + "type")), values.getProperty(p + "refreshToken"),
                        values.getProperty(p + "accessToken"), expiresAt));
            }
            activeId = values.getProperty("active");
        } else {
            // Before accounts, Kelp only had a player name in Options. Keep it as an offline account.
            accounts.add(Account.offline(Settings.playerName()));
            activeId = accounts.get(0).id();
        }
    }

    private static void save() {
        Properties values = new Properties();
        if (activeId != null) values.setProperty("active", activeId);
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            String p = i + ".";
            values.setProperty(p + "id", a.id());
            values.setProperty(p + "name", a.name());
            values.setProperty(p + "type", a.microsoft() ? "microsoft" : "offline");
            if (a.refreshToken() != null) values.setProperty(p + "refreshToken", a.refreshToken());
            if (a.accessToken() != null) values.setProperty(p + "accessToken", a.accessToken());
            values.setProperty(p + "expiresAt", String.valueOf(a.expiresAt()));
        }
        try {
            Files.createDirectories(FILE.getParent());
            // Write to a .part file first, so a crash halfway through never loses every account
            Path part = FILE.resolveSibling(FILE.getFileName() + ".part");
            try (Writer out = Files.newBufferedWriter(part)) {
                values.store(out, "Kelp's accounts. These are sign-in keys: don't share this file with anyone!");
            }
            try {
                // On Mac and Linux, only you can read it (Windows already keeps your AppData folder to you)
                Files.setPosixFilePermissions(part, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException windows) {
                // fine
            }
            Files.move(part, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Couldn't save " + FILE + ": " + e.getMessage());
        }
    }

    /** Forgets what's loaded, so the next call reads the file again. Only tests need this. */
    static synchronized void reload() {
        accounts = null;
        activeId = null;
    }
}
