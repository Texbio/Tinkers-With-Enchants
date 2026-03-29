package t.tinkerswithenchants.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Tinkers with Enchants configuration.
 */
public final class TWEConfig {

    // -----------------------------------------------------------------------
    // Server config
    // -----------------------------------------------------------------------

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        var serverPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER      = serverPair.getLeft();
        SERVER_SPEC = serverPair.getRight();
    }

    // -----------------------------------------------------------------------
    // Client config
    // -----------------------------------------------------------------------

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        var clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT      = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private TWEConfig() {}

    // -----------------------------------------------------------------------
    // Server config schema
    // -----------------------------------------------------------------------

    public static final class Server {

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Tinkers with Enchants server configuration").push("general");
            // Enchantability is now computed automatically from tool durability.
            // Future server-side configs can be added here.
            builder.pop();
        }
    }

    // -----------------------------------------------------------------------
    // Client config schema
    // -----------------------------------------------------------------------

    public static final class Client {

        /** Whether enchanted TConstruct items show the enchantment glint.
         *  Requires rejoining the world to take effect. */
        public final ForgeConfigSpec.BooleanValue showGlint;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Tinkers with Enchants client configuration").push("rendering");

            showGlint = builder
                    .comment(
                            "Whether enchanted Tinkers' Construct items show the enchantment glint (shimmer).",
                            "Requires rejoining the world to take effect.")
                    .define("showGlint", true);

            builder.pop();
        }
    }
}
