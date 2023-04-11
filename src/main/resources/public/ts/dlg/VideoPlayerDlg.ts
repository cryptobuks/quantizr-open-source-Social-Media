import { CompIntf } from "../comp/base/CompIntf";
import { Divc } from "../comp/core/Divc";
import { VideoPlayer } from "../comp/core/VideoPlayer";
import { DialogBase, DialogMode } from "../DialogBase";

/**
 * See also: AudioPlayerDlg (which is very similar)
 */
export class VideoPlayerDlg extends DialogBase {
    videoPlayer: VideoPlayer;

    constructor(private domId: string, private sourceUrl: string, mediaTitle: string, dialogMode: DialogMode) {
        super(mediaTitle || "Video", null, false, dialogMode);
    }

    renderDlg(): CompIntf[] {
        return [
            new Divc({ className: "videoContainer" }, [
                this.videoPlayer = new VideoPlayer({
                    id: this.domId + "-comp",
                    src: this.sourceUrl,
                    className: "videoPlayerElement",
                    // "ontimeupdate": () => { S.podcast.onTimeUpdate(this); },
                    // "oncanplay": () => { S.podcast.onCanPlay(this); },
                    controls: "controls",
                    autoPlay: "autoplay"
                    // "muted" : "false",
                    // "volume": "0.9",
                    // "preload": "auto"
                })
            ])

        ];
    }

    getVideoElement(): HTMLVideoElement {
        return this.videoPlayer.getVideoElement();
    }

    super_close = this.close;
    /* When the dialog closes we need to stop and remove the player */
    override close = () => {
        // console.log("VideoPlayerDialog cancel()");
        // todo-2: need to check over, and document flow of this function as it relates to calling "podcast.destroyPlayer(this);"
        this.destroyPlayer();
        this.super_close();
    }

    destroyPlayer = () => {
        const player = this.videoPlayer ? this.videoPlayer.getVideoElement() : null;
        if (player) {
            player.pause();
            player.remove();
            this.videoPlayer = null;
            this.close();
        }
    }
}
