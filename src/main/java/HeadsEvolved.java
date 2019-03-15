import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.plugin.meta.util.NonnullByDefault;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;

@Plugin(
    id = "headsevolved",
    name = "HeadsEvolved",
    version = "1.0.6",
    description = "Stores custom heads for Darwin Reforged")
public class HeadsEvolved {

  private static HeadsEvolved singleton;

  @Listener
  public void onServerFinishLoad(GameInitializationEvent event) {
    Sponge.getEventManager().registerListeners(this, new Listeners());
    Sponge.getCommandManager().register(this, hdbMain, "heads");
  }

  Gson gson = new Gson();
  ConfigurationHandle handle;

  @Inject
  @ConfigDir(sharedRoot = false)
  private Path root;

  @Listener
  public void onServerStart(GameStartedServerEvent event) {
    try {
      collectHeadsFromAPI();
    } catch (IOException e) {
      System.out.println("Failed to get head.");
    }
    File configFile = new File(root.toFile(), "headsevolved.conf");
    handle = new ConfigurationHandle(configFile);
    singleton = this;
  }

  static String apiLine = "https://minecraft-heads.com/scripts/api.php?tags=true&cat=";

  static String[] uuidBlacklist = {
    "c7299fa8d44b "
  }; // UUID's causing NumberFormatExceptions, currently only one exists in the entire database

  private void collectHeadsFromAPI() throws IOException {
    int totalHeads = 0;
    for (HeadObject.Category cat : HeadObject.Category.values()) {
      String connectionLine = apiLine + cat.toString().toLowerCase().replaceAll("_", "-");
      JsonArray array = readJsonFromUrl(connectionLine);
      System.out.println(cat.toString() + " : " + array.size());

      for (Object head : array) {
        if (head instanceof JsonObject) {
          JsonElement nameEl = ((JsonObject) head).get("name");
          JsonElement uuidEl = ((JsonObject) head).get("uuid");
          JsonElement valueEl = ((JsonObject) head).get("value");
          JsonElement tagsEl = ((JsonObject) head).get("tags");

          String name = nameEl.getAsString();
          String uuid = uuidEl.getAsString();
          String value = valueEl.getAsString();
          String tags = tagsEl instanceof JsonNull ? "None" : tagsEl.getAsString();

          boolean doAdd = true;
          for (String blackedUUID : uuidBlacklist) if (blackedUUID.equals(uuid)) doAdd = false;

          if (doAdd) {
            new HeadObject(name, uuid, value, tags, cat);
            totalHeads++;
          }
        }
      }
    }

    System.out.println("\nCollected : " + totalHeads + " heads from MinecraftHeadDB");
  }

  private String readAll(Reader rd) throws IOException {
    StringBuilder sb = new StringBuilder();
    int cp;
    while ((cp = rd.read()) != -1) {
      sb.append((char) cp);
    }
    return sb.toString();
  }

  public JsonArray readJsonFromUrl(String url) throws IOException {
    InputStream is = new URL(url).openStream();
    try {
      BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      String jsonText = readAll(rd);
      return (JsonArray) new JsonParser().parse(jsonText);
    } finally {
      is.close();
    }
  }

  public static HeadsEvolved getSingleton() {
    return singleton;
  }

  private CommandSpec hdbOpen =
      CommandSpec.builder()
          .description(Text.of("Opens Head Evo GUI"))
          .permission("he.open")
          .executor(new openInventory())
          .build();

  private CommandSpec hdbSearch =
      CommandSpec.builder()
          .description(Text.of("Searches for heads with matching tags or name"))
          .permission("he.open")
          .arguments(GenericArguments.onlyOne(GenericArguments.string(Text.of("query"))))
          .executor(new searchHeads())
          .build();

  private CommandSpec hdbMain =
      CommandSpec.builder()
          .description(Text.of("Main command"))
          .permission("he.open")
          .child(hdbOpen, "open")
          .child(hdbSearch, "find", "search")
          .build();

  private static class searchHeads implements CommandExecutor {

    @Override
    @NonnullByDefault
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      if (src instanceof Player) {
        Player player = (Player) src;
        String query = args.<String>getOne("query").get();
        if (query != "") {
          Set<HeadObject> headObjects = HeadObject.getByNameAndTag(query);
          ChestObject.openViewForSet(headObjects, player, "$search");
        }
      }
      return CommandResult.success();
    }
  }

  private static class openInventory implements CommandExecutor {

    @Override
    @NonnullByDefault
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      if (src instanceof Player) {
        Player player = (Player) src;
        try {
          new ChestObject(player);
        } catch (InstantiationException e) {
          player.sendMessage(
              Text.of(TextColors.GRAY, "// ", TextColors.RED, "Failed to open Head Database GUI"));
        }
      }
      return CommandResult.success();
    }
  }
}