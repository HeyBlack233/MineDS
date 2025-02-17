package heyblack.mineds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class MineDS {
    public static final String MOD_ID = "mineds";
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .orElseThrow(RuntimeException::new).getMetadata().getVersion().getFriendlyString();

    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mineds.json");
    public static final Logger LOGGER = LogManager.getLogger();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path LOG_PATH = FabricLoader.getInstance().getGameDir().resolve("MineDS");

}
