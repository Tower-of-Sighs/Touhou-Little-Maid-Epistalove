package com.sighs.touhou_little_maid_epistalove.ai.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.IntegerParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.StringParameter;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.touhou_little_maid_epistalove.ai.generator.AILetterGenerator;
import com.sighs.touhou_little_maid_epistalove.ai.parser.JsonLetterParser;
import com.sighs.touhou_little_maid_epistalove.ai.prompt.EnhancedPromptBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class WriteLetterTool implements ITool<WriteLetterTool.Args> {
    public static final String TOOL_ID = "write_letter";

    private static final String PROMPT_ID = "prompt";
    private static final String TONE_ID = "tone";
    private static final String FAVORABILITY_CHANGE_ID = "favorability_change";

    private static final String SUMMARY = """
            Compose and deliver a heartfelt letter to the owner.
            Use this when the player asks the maid to write a letter, or when the maid wants to send one proactively.
            """.trim();

    @Override
    public String id() {
        return TOOL_ID;
    }

    @Override
    public String summary(EntityMaid maid) {
        return SUMMARY;
    }

    @Override
    public Parameter parameters(ObjectParameter root, EntityMaid maid) {
        StringParameter prompt = StringParameter.create()
                .setDescription("The content guidance for the letter. Required.");

        StringParameter tone = StringParameter.create()
                .setDescription("Optional letter style tone. Omit to let the maid choose a suitable style.")
                .addEnumValues("sweet", "lonesome", "elegant", "gentle", "playful");

        IntegerParameter favorabilityChange = IntegerParameter.create()
                .setDescription("Optional. Positive to increase favorability, negative to decrease.");

        root.addProperties(PROMPT_ID, prompt, true);
        root.addProperties(TONE_ID, tone, false);
        root.addProperties(FAVORABILITY_CHANGE_ID, favorabilityChange, false);
        return root;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Integer parseEither(Either<Integer, String> either) {
        return either.map(i -> i, WriteLetterTool::parseIntSafe);
    }

    private static Either<Integer, String> intToEither(Integer i) {
        return Either.left(i);
    }

    @Override
    public Codec<Args> codec() {
        MapCodec<Optional<Integer>> deltaCodec = Codec.either(Codec.INT, Codec.STRING)
                .optionalFieldOf(FAVORABILITY_CHANGE_ID)
                .xmap(
                        optEither -> optEither.map(WriteLetterTool::parseEither),
                        optInt -> optInt.map(WriteLetterTool::intToEither)
                );

        return RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.fieldOf(PROMPT_ID).forGetter(Args::prompt),
                        Codec.STRING.optionalFieldOf(TONE_ID).forGetter(Args::tone),
                        deltaCodec.forGetter(Args::favorabilityChange)
                ).apply(instance, Args::new));
    }

    @Override
    public LLMCallback onCall(String toolCallId, Args args, LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        if (!(maid.getOwner() instanceof ServerPlayer owner)) {
            return callback.addToolResult("Failed to write letter: owner not found.", toolCallId);
        }
        String tone = args.tone().orElse(null);

        EnhancedPromptBuilder builder = new EnhancedPromptBuilder();
        JsonLetterParser parser = new JsonLetterParser(builder);
        AILetterGenerator generator = new AILetterGenerator(tone, args.prompt(), builder, parser);

        generator.generate(owner, maid, result -> {
            if (result.isEmpty()) {
                return;
            }
            ItemsUtil.giveItemToMaid(maid, result);
            args.favorabilityChange().ifPresent(delta -> {
                if (delta == 0) {
                    return;
                }
                FavorabilityManager fm = maid.getFavorabilityManager();
                if (delta > 0) {
                    fm.add(delta);
                } else {
                    fm.reduceWithoutLevel(-delta);
                }
            });
        });

        return callback.addToolResult("Writing and delivering a letter...", toolCallId);
    }

    @Override
    public String invocationSummary(Args result) {
        if (result.tone().isPresent()) {
            return "%s { tone=%s }".formatted(TOOL_ID, result.tone().get());
        }
        return TOOL_ID;
    }

    public record Args(String prompt, Optional<String> tone, Optional<Integer> favorabilityChange) {
    }
}
