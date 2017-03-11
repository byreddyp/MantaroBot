package net.kodehawa.mantarobot.commands;

import br.com.brjdevs.java.utils.collections.CollectionUtils;
import bsh.Interpreter;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.lib.mantarolang.CompiledFunction;
import net.kodehawa.lib.mantarolang.MantaroLang;
import net.kodehawa.lib.mantarolang.objects.LangObject;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.MantaroShard;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.modules.Category;
import net.kodehawa.mantarobot.modules.CommandPermission;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.SimpleCommand;
import net.kodehawa.mantarobot.utils.Async;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.IntSupplier;

import static net.kodehawa.mantarobot.utils.StringUtils.SPLIT_PATTERN;

public class OwnerCmd extends Module {
	private interface Evaluator {
		Object eval(GuildMessageReceivedEvent event, String code);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("Owner");
	private final String[] sleepQuotes = {"*goes to sleep*", "Mama, It's not night yet. *hmph*. okay. bye.", "*grabs pillow*", "*~~goes to sleep~~ goes to dreaming dimension*", "*grabs plushie*", "Momma, where's my Milk cup? *drinks and goes to sleep*"};

	public OwnerCmd() {
		super(Category.OWNER);
		add();
		blacklist();
		owner();
	}

	private void add() {
		super.register("varadd", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				String v = splitArgs(content)[1];

				switch (args[0]) {
					case "pat":
						MantaroData.getPatting().get().add(v);
						MantaroData.getPatting().save();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Added to pat list: " + v).queue();
						break;
					case "hug":
						MantaroData.getHugs().get().add(v);
						MantaroData.getHugs().save();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Added to hug list: " + v).queue();
						break;
					case "greeting":
						MantaroData.getGreeting().get().add(content.replace("greeting ", ""));
						MantaroData.getGreeting().save();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Added to greet list: " + content.replace("greeting ", "")).queue();
						break;
					case "splash":
						MantaroData.getSplashes().get().add(content.replace("splash ", ""));
						MantaroData.getSplashes().save();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Added to splash list: " + content.replace("splash ", "")).queue();
						break;
				}
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Add to list command")
					.setDescription("Adds a parameter to a list."
						+ "\n Arguments: \n pat <args[1]>, hug <args[1]>, greeting <content>, splash <content>")
					.build();
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.BOT_OWNER;
			}
		});
	}

	private void blacklist() {
		super.register("blacklist", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				if (args[0].equals("guild")) {
					if (args[1].equals("add")) {
						if (event.getJDA().getGuildById(args[2]) == null) return;
						MantaroData.getData().get().blacklistedGuilds.add(args[2]);
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Blacklisted Guild: " + event.getJDA().getGuildById(args[2])).queue();
						MantaroData.getData().save();
					} else if (args[1].equals("remove")) {
						MantaroData.getData().get().blacklistedGuilds.remove(args[2]);
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Unblacklisted Guild: " + args[2]).queue();
						MantaroData.getData().save();
					}
					return;
				}

				if (args[0].equals("user")) {
					if (args[1].equals("add")) {
						if (event.getJDA().getUserById(args[2]) == null) return;
						MantaroData.getData().get().blacklistedUsers.add(args[2]);
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Blacklisted User: " + event.getJDA().getUserById(args[2])).queue();
						MantaroData.getData().save();
					} else if (args[1].equals("remove")) {
						if (event.getJDA().getUserById(args[2]) == null) return;
						MantaroData.getData().get().blacklistedUsers.remove(args[2]);
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Unblacklisted User: " + event.getJDA().getUserById(args[2])).queue();
						MantaroData.getData().save();
					}
				}
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Blacklist command")
					.setDescription("Blacklists a user (user argument) or a guild (guild argument) by id.")
					.build();
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.BOT_OWNER;
			}
		});
	}

	public CompletableFuture<Void> notifyMusic(String content) {
		return CompletableFuture.allOf(MantaroBot.getInstance().getAudioManager().getMusicManagers().values()
			.stream()
			.filter(musicManager -> musicManager.getTrackScheduler().getCurrentTrack() != null)
			.filter(musicManager -> musicManager.getTrackScheduler().getCurrentTrack().getRequestedChannel() != null)
			.filter(musicManager -> musicManager.getTrackScheduler().getCurrentTrack().getRequestedChannel().canTalk())
			.map(musicManager -> musicManager.getTrackScheduler().getCurrentTrack().getRequestedChannel().sendMessage(content).submit())
			.map(future -> (CompletableFuture<Message>) future)
			.toArray(CompletableFuture[]::new));
	}

	private void owner() {
		Map<String, Evaluator> evals = new HashMap<>();
		evals.put("js", (event, code) -> {
			ScriptEngine script = new ScriptEngineManager().getEngineByName("nashorn");
			script.put("jda", event.getJDA());
			script.put("event", event);
			script.put("guild", event.getGuild());
			script.put("channel", event.getChannel());

			try {
				return script.eval(String.join("\n",
					"load(\"nashorn:mozilla_compat.js\");",
					"imports = new JavaImporter(java.util, java.io, java.net);",
					"(function() {",
					"with(imports) {",
					code,
					"}",
					"})()"
				));
			} catch (Exception e) {
				return e;
			}
		});

		evals.put("bsh", (event, code) -> {
			Interpreter interpreter = new Interpreter();
			try {
				interpreter.set("jda", event.getJDA());
				interpreter.set("event", event);
				interpreter.set("guild", event.getGuild());
				interpreter.set("channel", event.getChannel());

				return interpreter.eval(String.join("\n",
					"import *;",
					code
				));
			} catch (Exception e) {
				return e;
			}
		});

		evals.put("groovy", (event, code) -> {
			Binding b = new Binding();
			b.setVariable("jda", event.getJDA());
			b.setVariable("event", event);
			b.setVariable("guild", event.getGuild());
			b.setVariable("channel", event.getChannel());
			GroovyShell sh = new GroovyShell(b);
			try {
				return sh.evaluate(code);
			} catch (Exception e) {
				return e;
			}
		});

		evals.put("m", (event, code) -> {
			OptionalLong compileTime = OptionalLong.empty();
			OptionalLong executeTime = OptionalLong.empty();
			Object r;
			try {
				CompiledFunction<Pair<Long, List<LangObject>>> compiledFunction = new MantaroLang().compile(code);
				compileTime = OptionalLong.of(compiledFunction.timeTook());

				Pair<Long, List<LangObject>> run = compiledFunction.run();
				executeTime = OptionalLong.of(run.getKey());

				List<LangObject> returnList = run.getRight();

				r = returnList.isEmpty() ? null : returnList.size() == 1 ? returnList.get(0) : returnList;
			} catch (Exception e) {
				r = e;
			}

			OptionalLong runningTime = executeTime;
			compileTime.ifPresent(l -> event.getChannel().sendMessage("**MantaroLang Debug**\n**Compile Time**: " + l + " ms" + (runningTime.isPresent() ? "\n**Executing Time**: " + runningTime.orElse(0) + " ms" : "")).queue());

			return r;
		});

		super.register("owner", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				if (args.length < 1) {
					onHelp(event);
					return;
				}

				String option = args[0];

				if (option.equals("shutdown") || option.equals("restart")) {
					if (args.length == 2) {
						try {
							notifyMusic(args[1]).get();
						} catch (InterruptedException | ExecutionException ignored) {
						}
					}

					try {
						prepareShutdown(event);
					} catch (Exception e) {
						LOGGER.warn(EmoteReference.ERROR + "Couldn't prepare shutdown." + e.toString(), e);
						return;
					}

					//If we manage to get here, there's nothing else except us.

					//Here in Darkness, everything is okay.
					//Listen to the waves, and let them fade away.

					System.exit(option.equals("restart") ? 15 : 0);
					return;
				}

				if (option.equals("forceshutdown") || option.equals("forcerestart")) {
					if (args.length == 2) {
						try {
							notifyMusic(args[1]).get();
						} catch (InterruptedException | ExecutionException ignored) {
						}
					}

					try {
						prepareShutdown(event);
					} catch (Exception e) {
						LOGGER.warn(EmoteReference.ERROR + "Couldn't prepare shutdown. I don't care, I'm gonna restart anyway." + e.toString(), e);
					}

					//If we manage to get here, there's nothing else except us.

					//Here in Darkness, everything is okay.
					//Listen to the waves, and let them fade away.

					System.exit(option.equals("forcerestart") ? 15 : 0);
					return;
				}

				if (args.length < 2) {
					onHelp(event);
					return;
				}

				String value = args[1];

				if (option.equals("notifymusic")) {
					notifyMusic(args[1]);
					return;
				}

				String[] values = SPLIT_PATTERN.split(value, 2);
				if (values.length < 2) {
					onHelp(event);
					return;
				}

				String k = values[0], v = values[1];

				if (option.equals("scheduleshutdown") || option.equals("schedulerestart")) {
					boolean restart = option.equals("schedulerestart");
					if (k.equals("time")) {
						double s = Double.parseDouble(v);
						int millis = (int) (s * 1000);
						Async.asyncSleepThen(millis, () -> {
							try {
								prepareShutdown(event);
							} catch (Exception e) {
								LOGGER.warn(EmoteReference.ERROR + "Couldn't prepare shutdown. I don't care, I'm gonna restart anyway." + e.toString(), e);
							}
							System.exit(restart ? 15 : 0);
						}).run();

						event.getChannel().sendMessage(EmoteReference.STOPWATCH + " Sleeping in " + s + " seconds...").queue();
						return;
					}

					if (k.equals("connections")) {
						int connections = Integer.parseInt(v);

						IntSupplier currentConnections = () -> (int) event.getJDA().getVoiceChannels().stream().filter(voiceChannel -> voiceChannel.getMembers().contains(
							voiceChannel.getGuild().getSelfMember())).count();

						Async.startAsyncTask("Watching Thread.", s -> {
							if (currentConnections.getAsInt() > connections) return;

							try {
								prepareShutdown(event);
							} catch (Exception e) {
								LOGGER.warn("Couldn't prepare shutdown. I don't care, I'm gonna do it anyway." + e.toString(), e);
							}

							System.exit(restart ? 15 : 0);
							s.shutdown();
						}, 2);
						return;
					}

					onHelp(event);
					return;
				}

				if (option.equals("varadd")) {
					//TODO FUTURE
				}

				if (option.equals("eval")) {
					Evaluator evaluator = evals.get(k);
					if (evaluator == null) {
						onHelp(event);
						return;
					}

					Object result = evaluator.eval(event, v);
					boolean errored = result instanceof Exception;

					event.getChannel().sendMessage(new EmbedBuilder()
						.setAuthor("Evaluated " + (errored ? "and errored" : "with success"), null, event.getAuthor().getAvatarUrl())
						.setColor(errored ? Color.RED : Color.GREEN)
						.setDescription(result == null ? "Executed successfully with no objects returned" : ("Executed " + (errored ? "and errored: " : "successfully and returned: ") + result.toString()))
						.setFooter("Asked by: " + event.getAuthor().getName(), null)
						.build()
					).queue();

					return;
				}

				onHelp(event);
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return null; //TODO Uhh
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.BOT_OWNER;
			}

			@Override
			protected String[] splitArgs(String content) {
				return SPLIT_PATTERN.split(content, 2);
			}
		});
	}

	private void prepareShutdown(GuildMessageReceivedEvent event) {
		MantaroData.getData().save();
		MantaroBot.getInstance().getAudioManager().getMusicManagers().forEach((s, musicManager) -> {
			if (musicManager.getTrackScheduler() != null) musicManager.getTrackScheduler().stop();
		});

		Arrays.stream(MantaroBot.getInstance().getShards()).forEach(MantaroShard::prepareShutdown);

		event.getChannel().sendMessage(CollectionUtils.random(sleepQuotes)).complete();

		Arrays.stream(MantaroBot.getInstance().getShards()).forEach(mantaroShard -> mantaroShard.getJDA().shutdown(true));
	}
}