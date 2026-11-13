package ru.pasha.adaptationwheel;

import java.util.Properties;
import net.minecraftforge.common.ForgeConfigSpec;

/** Online capability is a build resource, never a client-supplied packet or server setting. */
public final class NeuralConfig {
    public static final boolean ONLINE=onlineBuild();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED,STRICT_SCHEMA;
    public static final ForgeConfigSpec.ConfigValue<String> ENDPOINT,MODEL,KEY_ENV;
    public static final ForgeConfigSpec.IntValue REQUESTS_PER_HOUR,TIMEOUT;
    static{var b=new ForgeConfigSpec.Builder();
        ENABLED=b.translation("analysis.adaptationwheel.config.enabled").comment("Opt-in external neural advice. Offline build never makes requests, regardless of this setting.").define("enabled",false);
        ENDPOINT=b.translation("analysis.adaptationwheel.config.endpoint").comment("Full HTTPS OpenAI-compatible /v1/chat/completions endpoint. Empty disables remote calls. HTTP is permitted only on loopback.").define("endpoint","");
        MODEL=b.translation("analysis.adaptationwheel.config.model").comment("Model available on your own configured service; no model or paid account is bundled.").define("model","");
        KEY_ENV=b.translation("analysis.adaptationwheel.config.key_env").comment("Name of the server process environment variable holding the API key. Never put the key itself here.").define("apiKeyEnvironment","ADAPTATION_WHEEL_API_KEY");
        STRICT_SCHEMA=b.translation("analysis.adaptationwheel.config.schema").comment("Request JSON Schema output; disable only for compatible services supporting JSON-object mode instead. Local validation always stays strict.").define("strictJsonSchema",true);
        REQUESTS_PER_HOUR=b.translation("analysis.adaptationwheel.config.requests").defineInRange("maximumRequestsPerHour",12,1,120);
        TIMEOUT=b.translation("analysis.adaptationwheel.config.timeout").defineInRange("requestTimeoutSeconds",8,1,20);SPEC=b.build();
    }
    private NeuralConfig(){}
    private static boolean onlineBuild(){try(var in=NeuralConfig.class.getResourceAsStream("/META-INF/adaptation-analysis.properties")){if(in==null)return false;var p=new Properties();p.load(in);return "true".equals(p.getProperty("online"));}catch(Exception ignored){return false;}}
    public static boolean configured(){return ONLINE&&SPEC.isLoaded()&&ENABLED.get()&&!ENDPOINT.get().isBlank()&&!MODEL.get().isBlank();}
}
