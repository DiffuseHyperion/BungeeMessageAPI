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

public class BungeeMessageAPI{
    private final Plugin plugin;
    private final WeakHashMap<Pair<String, String>, Queue<CompletableFuture<?>>> callbackMap = new WeakHashMap<>();
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

    public CompletableFuture<InetSocketAddress> IP(Player player) {
        return (CompletableFuture<InetSocketAddress>) getFuture1("IP", player);
    }

    public CompletableFuture<Pair<String, Integer>> PlayerCount(String servername) {
        return (CompletableFuture<Pair<String, Integer>>) getFuture2(new String[]{"PlayerCount", servername}, null);
    }

    public CompletableFuture<Pair<String, String[]>> PlayerList(String servername) {
        return (CompletableFuture<Pair<String, String[]>>) getFuture2(new String[]{"PlayerList", servername}, null);
    }

    public CompletableFuture<String[]> GetServers() {
        return (CompletableFuture<String[]>) getFuture1("GetServers", null);
    }

    public void Message(Player recipient, String message) {
        sendPluginMessage(new String[]{recipient.getName(), message}, null);
    }

    public CompletableFuture<String> GetServer() {
        return (CompletableFuture<String>) getFuture1("GetServer", null);
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

            callbacks = callbackMap.get(pair);
            if (callbacks != null) {
                if (!callbacks.isEmpty()) {
                    CompletableFuture<?> future = callbacks.poll();
                    if (future == null) {
                        return;
                    }
                    try {
                        switch (subchannel) {
                            case "IP":
                                String ip = in.readUTF();
                                int port = in.readInt();
                                ((CompletableFuture<InetSocketAddress>) future).complete(new InetSocketAddress(ip, port));
                                Bukkit.getLogger().info("done: ip");
                                break;
                            case "PlayerCount": {
                                String server = in.readUTF();
                                int playercount = in.readInt();
                                ((CompletableFuture<Pair<String, Integer>>) future).complete(new Pair<>(server, playercount));
                                Bukkit.getLogger().info("done: plrcount");
                                break;
                            }
                            case "PlayerList": {
                                String server = in.readUTF();
                                String[] playerList = in.readUTF().split(", ");
                                ((CompletableFuture<Pair<String, String[]>>) future).complete(new Pair<>(server, playerList));
                                Bukkit.getLogger().info("done: plrlist");
                                break;
                            }
                            case "GetServers":
                                String[] serverList = in.readUTF().split(", ");
                                ((CompletableFuture<String[]>) future).complete(serverList);
                                Bukkit.getLogger().info("done: getservers");
                                break;
                            default:
                                String servername = in.readUTF();
                                ((CompletableFuture<String>) future).complete(servername);
                                Bukkit.getLogger().info("done: getserver");
                                break;
                        }
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                }
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

    private BiFunction<Pair<String, String>, Queue<CompletableFuture<?>>, Queue<CompletableFuture<?>>> computeQueueValue(CompletableFuture<?> queueValue) {
        return (key, value) -> {
            if (value == null) value = new ArrayDeque<>();
            value.add(queueValue);
            return value;
        };
    }

    private CompletableFuture<?> getFuture2(String[] list, @Nullable Player player) {
        sendPluginMessage(list, player);
        CompletableFuture<?> future = new CompletableFuture<>();

        synchronized (callbackMap) {
            callbackMap.compute(new Pair<>(list[0], list[1]), computeQueueValue(future));
        }

        return future;
    }

    private CompletableFuture<?> getFuture1(String string, @Nullable Player player) {
        sendPluginMessage(new String[]{string}, player);
        CompletableFuture<?> future = new CompletableFuture<>();

        synchronized (callbackMap) {
            callbackMap.compute(new Pair<>(string, ""), computeQueueValue(future));
        }

        return future;
    }
}
