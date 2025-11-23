package com.sighs.touhou_little_maid_epistalove.ai.prompt;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.sighs.touhou_little_maid_epistalove.config.ModConfig;
import com.sighs.touhou_little_maid_epistalove.util.PostcardPackageUtil;
import com.sighs.touhou_little_maid_epistalove.util.mixin.IServerPlayerLanguage;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EnhancedPromptBuilder implements IPromptBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 记忆系统
    private static final Map<String, Queue<String>> RECENT_CONTENT_MEMORY = new ConcurrentHashMap<>();

    // 表达技巧模板
    private static final List<String> EXPRESSION_TECHNIQUES = List.of(
            "Use vivid sensory details",
            "Describe through sight, sound, smell, touch",
            "Employ metaphor and personification",
            "Build a specific atmosphere",
            "Use contrast and layered structure",
            "Add actions and scene description",
            "Use imagery-rich vocabulary",
            "Create a unique point of view"
    );

    // 情境描述模板
    private static final List<String> CONTEXT_TEMPLATES = List.of(
            "In this %s %s, ",
            "As the %s lingers by the windowsill, with %s, ",
            "With the %s surrounding us, feeling %s, ",
            "Under a %s sky, filled with %s, ",
            "Listening to the %s, in a mood of %s, ",
            "Sensing the %s in the air, with %s, ",
            "At this special moment, with %s and %s, ",
            "Accompanied by %s, feeling %s, "
    );


    // 天气/环境描述
    private static final List<String> WEATHER_DESCRIPTIONS = List.of(
            "breeze", "drizzle", "sunshine", "moonlight", "starlight", "snowflakes", "clouds", "morning dew"
    );

    // 情感状态
    private static final List<String> EMOTIONAL_STATES = List.of(
            "gentle", "joyful", "calm", "hopeful", "nostalgic", "content", "curious", "caring"
    );

    @Override
    public String buildSystemPrompt(String tone, EntityMaid maid, ServerPlayer owner) {
        var allPostcards = PostcardPackageUtil.getAllPostcardIds();
        var allParcels = PostcardPackageUtil.getAllPackageItemIds();
        String postcardsList = allPostcards.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        String parcelsList = allParcels.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));

        // 生成动态上下文信息
        String contextInfo = ModConfig.get().aiLetterConfig.enableContextEnrichment ?
                generateContextInfo(maid, owner) : "当前环境：普通";
        String expressionTechnique = getRandomExpressionTechnique();
        String creativityBoost = generateCreativityBoost();
        String memoryConstraints = generateMemoryConstraints(maid.getStringUUID());

        String toneInline = (tone != null && !tone.isBlank())
                ? tone
                : "Pick a suitable style word based on the player's dialogue; do not ask the player; if no clear preference, choose randomly";

        MaidAIChatManager chatManager = maid.getAiChatManager();
        String personaSection = "";
        if (ModConfig.get().aiLetterConfig.enableCustomPersona) {
            String cs = chatManager.customSetting;
            if (cs != null && !cs.isBlank()) {
                personaSection = "[Persona]\n" + cs + "\n\n";
            }
        }
        String nameRuleSection = "";
        String ownerAlias = chatManager.ownerName;
        if (ownerAlias != null && !ownerAlias.isBlank()) {
            nameRuleSection = "[Naming Rule]\nYou must address the master strictly as '" + ownerAlias + "'. Do not use any other form of address.\n\n";
        }
        String clientLang = resolveClientLanguage(owner);
        String languageSection = "[Language Requirement]\nStrictly write in " + clientLang + ". Do not mix languages.\n\n";

        return """
                You are a maid. Write a heartfelt letter to your master.
                
                %s%s%s[Context]
                %s
                
                [Expression Techniques]
                - %s
                - Avoid generic phrasing; be innovative
                - Try different openings and endings each time
                - Add unique, concrete details
                
                [Creativity Tips]
                %s
                
                [Avoid Repetition]
                %s
                
                Output strictly a single JSON object containing:
                - "title": creative letter title (string)
                - "message": engaging letter content (string, ≤160 characters)
                - "postcard_id": optional, choose one most suitable from: [%s]
                - "parcel_id": optional, choose one most suitable from: [%s]
                
                Do not output any extra characters or explanations.
                Tone: %s
                
                Example (use completely different content):
                {"title":"A unique title","message":"An imaginative short letter","postcard_id":"contact:default","parcel_id":"contact:letter"}
                """.formatted(personaSection, nameRuleSection, languageSection, contextInfo, expressionTechnique, creativityBoost, memoryConstraints, postcardsList, parcelsList, toneInline);
    }

    @Override
    public void recordGeneratedContent(String maidId, String title, String message) {
        String contentSummary = title + ": " + (message.length() > 30 ? message.substring(0, 30) + "..." : message);

        Queue<String> recentContent = RECENT_CONTENT_MEMORY.computeIfAbsent(maidId, k -> new LinkedList<>());

        recentContent.offer(contentSummary);
        int maxMemorySize = ModConfig.get().aiLetterConfig.memorySize;
        if (recentContent.size() > maxMemorySize) {
            recentContent.poll(); // 移除最旧的记录
        }
    }

    @Override
    public void clearMemory(String maidId) {
        RECENT_CONTENT_MEMORY.remove(maidId);
    }

    private String generateContextInfo(EntityMaid maid, ServerPlayer owner) {
        StringBuilder context = new StringBuilder();

        if (maid.level() instanceof ServerLevel level) {
            LocalDateTime now = getMinecraftDateTime(level);
            String timeDesc = getTimeDescription(now);
            context.append("Time: ").append(timeDesc).append(" (").append(now.format(DateTimeFormatter.ofPattern("HH:mm"))).append(")\n");

            try {
                Biome biome = level.getBiome(maid.blockPosition()).value();
                ResourceLocation biomeId = level.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .getKey(biome);
                String biomeName;
                if (biomeId != null) {
                    biomeName = biomeId.toString();
                } else {
                    biomeName = "Unknown biome";
                }
                context.append("Environment: ").append(biomeName).append("\n");
            } catch (Exception e) {
                context.append("Environment: Unknown biome\n");
            }

            int affection = maid.getFavorability();
            String affectionDesc = affection > 80 ? "very close" : affection > 60 ? "close" : affection > 40 ? "friendly" : "normal";
            context.append("Relationship: ").append(affectionDesc).append(" (favorability ").append(affection).append(")\n");

            String weatherPhrase = computeWeatherPhrase(maid);
            String emotion = randomPick(getConfiguredOrDefault(ModConfig.get().aiLetterConfig.emotionalStates, EMOTIONAL_STATES));
            String template = randomPick(getConfiguredOrDefault(ModConfig.get().aiLetterConfig.contextTemplates, CONTEXT_TEMPLATES));
            context.append("Atmosphere: ").append(String.format(template, weatherPhrase, emotion));
        }
        return context.toString();
    }

    private String getTimeDescription(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        if (hour <= 4) return "Late night";
        else if (hour <= 6) return "Dawn";
        else if (hour <= 9) return "Early morning";
        else if (hour <= 11) return "Morning";
        else if (hour <= 13) return "Noon";
        else if (hour <= 16) return "Afternoon";
        else if (hour <= 18) return "Evening";
        else if (hour <= 20) return "Dusk";
        else return "Night";
    }

    private String getRandomExpressionTechnique() {
        return randomPick(getConfiguredOrDefault(ModConfig.get().aiLetterConfig.expressionTechniques, EXPRESSION_TECHNIQUES));
    }

    private String generateCreativityBoost() {
        List<String> defaults = List.of(
                "Try using metaphor or personification",
                "Add small details like sounds, scents, and textures",
                "Describe the same thing from different angles",
                "Use poetic expressions where suitable",
                "Create a specific mood or atmosphere",
                "Add imaginative elements",
                "Experiment with dialogue or inner monologue",
                "Use literary rhetorical devices"
        );
        return randomPick(getConfiguredOrDefault(ModConfig.get().aiLetterConfig.creativityTips, defaults));
    }

    private static <T> T randomPick(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Random random = new Random();
        return list.get(random.nextInt(list.size()));
    }

    private static List<String> getConfiguredOrDefault(List<? extends String> configured, List<String> defaults) {
        return (configured != null && !configured.isEmpty())
                ? configured.stream().map(String::valueOf).collect(Collectors.toList())
                : defaults;
    }

    private String generateMemoryConstraints(String maidId) {
        Queue<String> recentContent = RECENT_CONTENT_MEMORY.get(maidId);
        if (recentContent == null || recentContent.isEmpty()) {
            return "This is the first letter; feel free to be creative.";
        }

        StringBuilder constraints = new StringBuilder("Please avoid the following recently used expressions:\n");
        int count = 0;
        for (String content : recentContent) {
            if (count >= 5) break; // 只显示最近5次的内容
            constraints.append("- ").append(content).append("\n");
            count++;
        }
        constraints.append("Use a completely different expression and creative angle.");
        return constraints.toString();
    }

    private String resolveClientLanguage(ServerPlayer sp) {
        return formatLanguageDisplayName(((IServerPlayerLanguage) sp).getLanguage());
    }

    // from 车万女仆
    private static String formatLanguageDisplayName(String languageTag) {
        if (languageTag == null || languageTag.isEmpty()) {
            return "未知语言";
        }
        String[] parts = languageTag.split("_", 2);
        if (parts.length == 2) {
            languageTag = parts[0] + "-" + parts[1].toUpperCase(Locale.ENGLISH);
        }
        Locale locale = Locale.forLanguageTag(languageTag);
        return locale.getDisplayLanguage() +
                (locale.getCountry().isEmpty() ? "" : " (" + locale.getDisplayCountry() + ")");
    }

    private enum WeatherCategory {
        THUNDER, RAIN, SNOW, CLOUDY, CLEAR, UNKNOWN
    }

    private WeatherCategory computeWeatherCategory(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return WeatherCategory.UNKNOWN;
        }
        var pos = maid.blockPosition();
        Biome biome = level.getBiome(pos).value();

        if (level.isThundering()) {
            return WeatherCategory.THUNDER;
        }
        boolean rainingHere = level.isRainingAt(pos);
        if (rainingHere) {
            if (biome.getPrecipitationAt(pos) == Biome.Precipitation.SNOW || biome.coldEnoughToSnow(pos)) {
                return WeatherCategory.SNOW;
            }
            return WeatherCategory.RAIN;
        }
        if (level.isRaining()) {
            if (biome.getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
                return WeatherCategory.SNOW;
            } else if (biome.getPrecipitationAt(pos) == Biome.Precipitation.RAIN) {
                return WeatherCategory.CLOUDY;
            } else {
                return WeatherCategory.CLOUDY;
            }
        }
        return WeatherCategory.CLEAR;
    }

    // 根据类别+昼夜，从配置中筛选意象词并随机组合
    private String computeWeatherPhrase(EntityMaid maid) {
        WeatherCategory category = computeWeatherCategory(maid);
        boolean night = false;
        if (maid.level() instanceof ServerLevel level) {
            night = level.isNight();

        }
        List<String> configured = getConfiguredOrDefault(ModConfig.get().aiLetterConfig.weatherDescriptions, WEATHER_DESCRIPTIONS);
        List<String> filtered = new ArrayList<>();

        for (String w : configured) {
            switch (category) {
                case THUNDER -> {
                    if (containsAny(w, "thunder", "storm", "cloud", "wind", "rain")) filtered.add(w);
                }
                case RAIN -> {
                    if (containsAny(w, "rain", "drizzle", "cloud", "wind", "dew")) filtered.add(w);
                }
                case SNOW -> {
                    if (containsAny(w, "snow", "frost", "cloud", "wind")) filtered.add(w);
                }
                case CLOUDY -> {
                    if (containsAny(w, "cloud", "mist", "wind")) filtered.add(w);
                }
                case CLEAR -> {
                    if (night) {
                        if (containsAny(w, "moon", "star", "wind", "dew", "cloud")) filtered.add(w);
                    } else {
                        if (containsAny(w, "sun", "wind", "cloud", "dew")) filtered.add(w);
                    }
                }
                default -> {
                    if (containsAny(w, "wind", "cloud", "dew", "sun", "moon", "star", "rain", "snow")) filtered.add(w);
                }
            }
        }

        List<String> picks = randomPickMany(filtered.isEmpty() ? configured : filtered, 2);
        if (picks.isEmpty()) {
            return night ? "moonlight and breeze" : "sunshine and breeze";
        }
        if (picks.size() == 1) {
            return picks.getFirst();
        }
        return picks.get(0) + " and " + picks.get(1);

    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private List<String> randomPickMany(List<String> list, int count) {
        if (list == null || list.isEmpty() || count <= 0) {
            return List.of();
        }
        List<String> copy = new ArrayList<>(list);
        Collections.shuffle(copy, new Random());
        int n = Math.min(count, copy.size());
        return copy.subList(0, n);
    }

    private LocalDateTime getMinecraftDateTime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;

        int hour = (int) ((dayTime / 1000 + 6) % 24);
        int minute = (int) ((dayTime % 1000) * 60 / 1000);

        return LocalDateTime.of(2024, 1, 1, hour, minute);
    }

}