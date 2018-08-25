package fredboat.command.music.info;

import static fredboat.main.LauncherKt.getBotController;
import static fredboat.util.MessageBuilderKt.localMessageBuilder;

import com.fredboat.sentinel.entities.Embed;
import com.fredboat.sentinel.entities.Field;
import com.google.common.base.Splitter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import fredboat.audio.player.GuildPlayer;
import fredboat.commandmeta.abs.CommandContext;
import fredboat.commandmeta.abs.IMusicCommand;
import fredboat.commandmeta.abs.JCommand;
import fredboat.messaging.internal.Context;
import fredboat.util.MessageBuilder;
import fredboat.util.rest.GeniusAPI;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class LyricsCommand extends JCommand implements IMusicCommand {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LyricsCommand.class);
    private static final int COLOUR_CODE = 10300584;
    private GeniusAPI geniusAPI;

    public LyricsCommand(GeniusAPI geniusAPI, @NotNull String name, @NotNull String... aliases) {
        super(name, aliases);

        this.geniusAPI = geniusAPI;
    }

    @Override
    public void onInvoke(@NotNull CommandContext context) {
        GuildPlayer player = getBotController().getPlayerRegistry().getExisting(context.getGuild());

        if (player != null && player.getPlayingTrack() != null) {
            AudioTrack playingTrack = player.getPlayingTrack().getTrack();

            geniusAPI.getLyrics(playingTrack).ifPresent(lyrics -> {
                MessageBuilder mb = localMessageBuilder();
                String longMessage;

                mb.append(context.i18nFormat("lyricsForSong", lyrics.getTitle()));
                mb.append("\n \n");
                longMessage = mb.build() + lyrics.getLyrics();

                for (String s : Splitter.fixedLength(1800).split(longMessage)) {
                    Embed embed = new Embed();
                    embed.setColor(COLOUR_CODE);
                    List<Field> fieldList = new ArrayList<>();
                    Field field = new Field();
                    field.setTitle("Lyrics");
                    field.setBody(s);
                    fieldList.add(field);
                    embed.setFields(fieldList);

                    context.reply(embed);
                }
            });
        } else {
            context.reply(context.i18n("npNotPlaying"));
        }
    }

    @NotNull
    @Override
    public String help(@NotNull Context context) {
        return "{0}\n#" + context.i18n("helpLyricsCommand");
    }
}
