package tk.yjservers;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.javatuples.Pair;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * This API was possible by leonardost, check his api out
 * @see <a href="https://github.com/leonardosnt/BungeeChannelApi/blob/master/src/main/java/io/github/leonardosnt/bungeechannelapi/BungeeChannelApi.java">leonardosnt's api</a>
 * @see <a href="https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/">Bukkit and bungee plugin channel/</a>
 */
public class BungeeMessageAPI{
    private final Plugin plugin;
    private final WeakHashMap<Pair<String, String>, Queue<Consumer<?>>> callbackMap = new WeakHashMap<>();
    private final PluginMessageListener messageListener;

    /**
     * An API to send plugin messages to Bungeecord.
     * @implSpec You need to provide your plugin to send the messages.
     * @param p Your plugin.
     */
    public BungeeMessageAPI(Plugin p) {
        plugin = Objects.requireNonNull(p, "Plugin cannot be null!");

        messageListener = this::onPluginMessageReceived;

        Messenger messenger = Bukkit.getServer().getMessenger();

        // incase of any wild stuff
        messenger.unregisterOutgoingPluginChannel(plugin, "BungeeCord");
        messenger.unregisterIncomingPluginChannel(plugin, "BungeeCord", messageListener);

        messenger.registerOutgoingPluginChannel(plugin, "BungeeCord");
        messenger.registerIncomingPluginChannel(plugin, "BungeeCord", messageListener);
    }

    public void Connect(Player player, String servername) {
        sendPluginMessage(new String[]{"Connect", servername}, player);
    }

    public CompletableFuture<InetSocketAddress>IP(Player player) {
        sendPluginMessage(new String[]{"IP"}, player);
        callbackMap.compute(new Pair<>("IP", player.getName()), computeQueueValue(consumer));
    }

    public CompletableFuture<Pair<String, Integer>> PlayerCount(String servername) {
        sendPluginMessage(new String[]{"PlayerCount", servername}, null);
    }

    public CompletableFuture<Pair<String, String[]>> PlayerList(String servername) {
        sendPluginMessage(new String[]{"PlayerList", servername}, null);
    }

    public CompletableFuture<String[]> GetServers() {
        sendPluginMessage(new String[]{"GetServers"}, null);
    }

    public void Message(Player recipient, String message) {
        sendPluginMessage(new String[]{recipient.getName(), message}, null);
    }

    public CompletableFuture<String> GetServer() {
        sendPluginMessage(new String[]{"GetServer"}, null);
    }

    public void unregister() {
        Messenger messenger = Bukkit.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, "BungeeCord", messageListener);
        messenger.unregisterOutgoingPluginChannel(plugin);
        callbackMap.clear();
    }

    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equalsIgnoreCase("BungeeCord")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        synchronized (callbackMap) {
            Queue<CompletableFuture<?>> callbacks;

            Pair<String, String> pair;
            if (subchannel.equals("PlayerCount") || subchannel.equals("PlayerList")) {
                String identifier = in.readUTF();
                pair = new Pair<>(subchannel, identifier);
            } else if (subchannel.equals("IP") || subchannel.equals("GetServer") || subchannel.equals("GetServers")) {
                pair = new Pair<>(subchannel, "");
            } else {
                return;
            }
        }
    }

    private Player getRandomPlayer() {
        return Objects.requireNonNull(Bukkit.getOnlinePlayers()[0], "There needs to be 1 player on the server!");
    }

    private void sendPluginMessage(String[] messages, @Nullable Player p) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        for (String s : messages) {
            try {
                out.writeUTF(s);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (p == null) {
            Objects.requireNonNull(getRandomPlayer(), "There needs to be 1 player on the server!").sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } else {
            p.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        }

    }
}
