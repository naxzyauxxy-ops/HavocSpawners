package dev.havoc.spawners.bedrock;

import dev.havoc.spawners.HavocSpawners;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bridge to Floodgate / Cumulus, reached entirely by reflection.
 * <p>
 * Bedrock clients cannot see Java's dialog screens - Geyser does not translate those packets - so a
 * Bedrock player right-clicking a spawner would otherwise get nothing at all. This sends them a
 * native Bedrock form instead.
 * <p>
 * Nothing here is a compile or runtime dependency: if Floodgate is missing, {@link #available()}
 * stays false and every player keeps the Java dialogs.
 */
public final class BedrockBridge {

    private final HavocSpawners plugin;

    private boolean available;
    private Object api;
    private Method isFloodgatePlayer;
    private Method sendFormBuilder;

    private Class<?> simpleBuilder;
    private Class<?> customBuilder;
    private Class<?> modalBuilder;
    private Class<?> customResponse;

    private Method simpleFactory;
    private Method customFactory;
    private Method modalFactory;

    public BedrockBridge(HavocSpawners plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        available = false;
        api = null;
        if (!plugin.settings().bedrockEnabled) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null
                && Bukkit.getPluginManager().getPlugin("Floodgate") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return;
            }
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);

            Class<?> formBuilderClass = Class.forName("org.geysermc.cumulus.form.util.FormBuilder");
            // Passing the builder lets Floodgate call build() for us.
            sendFormBuilder = apiClass.getMethod("sendForm", UUID.class, formBuilderClass);

            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Class<?> customForm = Class.forName("org.geysermc.cumulus.form.CustomForm");
            Class<?> modalForm = Class.forName("org.geysermc.cumulus.form.ModalForm");
            simpleFactory = simpleForm.getMethod("builder");
            customFactory = customForm.getMethod("builder");
            modalFactory = modalForm.getMethod("builder");

            simpleBuilder = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
            customBuilder = Class.forName("org.geysermc.cumulus.form.CustomForm$Builder");
            modalBuilder = Class.forName("org.geysermc.cumulus.form.ModalForm$Builder");
            customResponse = Class.forName("org.geysermc.cumulus.response.CustomFormResponse");

            available = true;
            plugin.getLogger().info("Floodgate detected - Bedrock players will get native forms.");
        } catch (Throwable ex) {
            available = false;
            plugin.getLogger().warning("Floodgate is installed but its form API could not be reached ("
                    + ex.getClass().getSimpleName() + "). Bedrock players will see Java dialogs.");
        }
    }

    public boolean available() {
        return available;
    }

    /** True when this player is connected through Geyser/Floodgate. */
    public boolean isBedrock(Player player) {
        if (!available) {
            return false;
        }
        try {
            Object result = isFloodgatePlayer.invoke(api, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (Throwable ex) {
            return false;
        }
    }

    /** Whether this player's screens should be Bedrock forms rather than Java dialogs. */
    public boolean useForms(Player player) {
        if (!available) {
            return false;
        }
        return plugin.settings().bedrockForceForms || isBedrock(player);
    }

    private void send(Player player, Object builder) {
        try {
            sendFormBuilder.invoke(api, player.getUniqueId(), builder);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not send a Bedrock form: " + ex.getMessage());
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        return owner.getMethod(name, params);
    }

    // ------------------------------------------------------------------ simple

    /** A button list. Each button carries its own action, so no index bookkeeping is needed. */
    public final class Simple {
        private final Player player;
        private Object builder;
        private final List<Consumer<Player>> actions = new ArrayList<>();
        private boolean broken;

        Simple(Player player, String title, String content) {
            this.player = player;
            try {
                builder = simpleFactory.invoke(null);
                find(simpleBuilder, "title", String.class).invoke(builder, title);
                if (content != null && !content.isEmpty()) {
                    find(simpleBuilder, "content", String.class).invoke(builder, content);
                }
            } catch (Throwable ex) {
                broken = true;
            }
        }

        public Simple button(String text, Consumer<Player> action) {
            if (broken) {
                return this;
            }
            try {
                int index = actions.size();
                actions.add(action);
                Consumer<Object> callback = response -> run(index);
                find(simpleBuilder, "button", String.class, Consumer.class).invoke(builder, text, callback);
            } catch (Throwable ex) {
                broken = true;
            }
            return this;
        }

        private void run(int index) {
            if (index < 0 || index >= actions.size() || !player.isOnline()) {
                return;
            }
            Consumer<Player> action = actions.get(index);
            if (action != null) {
                action.accept(player);
            }
        }

        public void send() {
            if (!broken) {
                BedrockBridge.this.send(player, builder);
            }
        }
    }

    public Simple simple(Player player, String title, String content) {
        return new Simple(player, title, content);
    }

    // ------------------------------------------------------------------ custom

    /** Values a custom form came back with, addressed by the order they were added. */
    public final class Answers {
        private final Object response;

        Answers(Object response) {
            this.response = response;
        }

        public float slider(int index, float fallback) {
            try {
                Object value = find(customResponse, "asSlider", int.class).invoke(response, index);
                return value instanceof Number number ? number.floatValue() : fallback;
            } catch (Throwable ex) {
                return fallback;
            }
        }

        public boolean toggle(int index, boolean fallback) {
            try {
                Object value = find(customResponse, "asToggle", int.class).invoke(response, index);
                return value instanceof Boolean bool ? bool : fallback;
            } catch (Throwable ex) {
                return fallback;
            }
        }

        public String input(int index, String fallback) {
            try {
                Object value = find(customResponse, "asInput", int.class).invoke(response, index);
                return value instanceof String text ? text : fallback;
            } catch (Throwable ex) {
                return fallback;
            }
        }

        public int dropdown(int index, int fallback) {
            try {
                Object value = find(customResponse, "asDropdown", int.class).invoke(response, index);
                return value instanceof Number number ? number.intValue() : fallback;
            } catch (Throwable ex) {
                return fallback;
            }
        }
    }

    /**
     * Sliders, toggles and inputs.
     * <p>
     * Labels count as components on Bedrock, so every helper here returns the index the value will
     * arrive at - store it and read it back with the same number.
     */
    public final class Custom {
        private final Player player;
        private Object builder;
        private int index;
        private boolean broken;

        Custom(Player player, String title) {
            this.player = player;
            try {
                builder = customFactory.invoke(null);
                find(customBuilder, "title", String.class).invoke(builder, title);
            } catch (Throwable ex) {
                broken = true;
            }
        }

        public Custom label(String text) {
            if (broken) {
                return this;
            }
            try {
                find(customBuilder, "label", String.class).invoke(builder, text);
                index++;
            } catch (Throwable ex) {
                broken = true;
            }
            return this;
        }

        public int slider(String text, float min, float max, float step, float initial) {
            if (broken) {
                return index;
            }
            try {
                find(customBuilder, "slider", String.class, float.class, float.class, float.class, float.class)
                        .invoke(builder, text, min, max, step, initial);
                return index++;
            } catch (Throwable ex) {
                broken = true;
                return index;
            }
        }

        public int toggle(String text, boolean initial) {
            if (broken) {
                return index;
            }
            try {
                find(customBuilder, "toggle", String.class, boolean.class).invoke(builder, text, initial);
                return index++;
            } catch (Throwable ex) {
                broken = true;
                return index;
            }
        }

        public int input(String text, String placeholder, String initial) {
            if (broken) {
                return index;
            }
            try {
                find(customBuilder, "input", String.class, String.class, String.class)
                        .invoke(builder, text, placeholder, initial);
                return index++;
            } catch (Throwable ex) {
                broken = true;
                return index;
            }
        }

        public Custom onSubmit(java.util.function.BiConsumer<Player, Answers> handler) {
            if (broken) {
                return this;
            }
            try {
                Consumer<Object> callback = response -> {
                    if (player.isOnline()) {
                        handler.accept(player, new Answers(response));
                    }
                };
                find(customBuilder, "validResultHandler", Consumer.class).invoke(builder, callback);
            } catch (Throwable ex) {
                broken = true;
            }
            return this;
        }

        public void send() {
            if (!broken) {
                BedrockBridge.this.send(player, builder);
            }
        }
    }

    public Custom custom(Player player, String title) {
        return new Custom(player, title);
    }

    // ------------------------------------------------------------------ modal

    /** Two-button yes/no screen. */
    public final class Modal {
        private final Player player;
        private Object builder;
        private Consumer<Player> onYes;
        private Consumer<Player> onNo;
        private boolean broken;

        Modal(Player player, String title, String content) {
            this.player = player;
            try {
                builder = modalFactory.invoke(null);
                find(modalBuilder, "title", String.class).invoke(builder, title);
                find(modalBuilder, "content", String.class).invoke(builder, content);
            } catch (Throwable ex) {
                broken = true;
            }
        }

        public Modal yes(String text, Consumer<Player> action) {
            this.onYes = action;
            if (!broken) {
                try {
                    find(modalBuilder, "button1", String.class).invoke(builder, text);
                } catch (Throwable ex) {
                    broken = true;
                }
            }
            return this;
        }

        public Modal no(String text, Consumer<Player> action) {
            this.onNo = action;
            if (!broken) {
                try {
                    find(modalBuilder, "button2", String.class).invoke(builder, text);
                } catch (Throwable ex) {
                    broken = true;
                }
            }
            return this;
        }

        public void send() {
            if (broken) {
                return;
            }
            try {
                Class<?> modalResponse = Class.forName("org.geysermc.cumulus.response.ModalFormResponse");
                Method clickedFirst = modalResponse.getMethod("clickedFirst");
                Consumer<Object> callback = response -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    boolean first;
                    try {
                        Object value = clickedFirst.invoke(response);
                        first = value instanceof Boolean bool && bool;
                    } catch (Throwable ex) {
                        return;
                    }
                    Consumer<Player> action = first ? onYes : onNo;
                    if (action != null) {
                        action.accept(player);
                    }
                };
                find(modalBuilder, "validResultHandler", Consumer.class).invoke(builder, callback);
            } catch (Throwable ex) {
                return;
            }
            BedrockBridge.this.send(player, builder);
        }
    }

    public Modal modal(Player player, String title, String content) {
        return new Modal(player, title, content);
    }
}
