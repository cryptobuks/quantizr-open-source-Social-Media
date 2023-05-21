import { dispatch, getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { CompIntf } from "../comp/base/CompIntf";
import { Checkbox } from "../comp/core/Checkbox";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { FlexRowLayout } from "../comp/core/FlexRowLayout";
import { Heading } from "../comp/core/Heading";
import { Icon } from "../comp/core/Icon";
import { Selection } from "../comp/core/Selection";
import { Span } from "../comp/core/Span";
import { TabHeading } from "../comp/core/TabHeading";
import { TextArea } from "../comp/core/TextArea";
import { Constants as C } from "../Constants";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";

export class TTSView extends AppTab<any, TTSView> {

    static textAreaState: Validator = new Validator();

    constructor(data: TabIntf<any, TTSView>) {
        super(data);
        data.inst = this;
    }

    spanClick = (evt: Event) => {
        let clickId = S.domUtil.getPropFromDom(evt, "id");
        if (clickId && clickId.startsWith("tts")) {
            clickId = clickId.substring(3);
            const idx = parseInt(clickId);
            if (idx >= 0) {
                S.speech.jumpToIdx(idx);
            }
        }
    }

    override preRender(): boolean {
        const ast = getAs();
        const speakBtn = !ast.mobileMode ? new Icon({
            className: "fa fa-volume-up fa-2x clickable",
            // This mouseover stuff is compensating for the fact that when the onClick gets called
            // it's a problem that by then the text selection "might" have gotten lost. This can happen.
            onMouseOver: () => { S.quanta.selectedForTts = window.getSelection().toString(); },
            onMouseOut: () => { S.quanta.selectedForTts = null; },
            onClick: () => S.speech.speakSelOrClipboard(true),
            title: "Text-to-Speech: Speak from input below, Selected Text, or Clipboard"
        }) : null;

        // make the entire tab area a drop target for speaking text.
        S.domUtil.setDropHandler(this.attribs, (evt: DragEvent) => {
            for (const item of evt.dataTransfer.items) {
                // console.log("DROP(c) kind=" + item.kind + " type=" + item.type);
                if (item.kind === "string") {
                    item.getAsString(async (s) => S.speech.speakText(s));
                    return;
                }
            }
        });

        const appendTextBtn = !ast.mobileMode && S.speech.queuedSpeech?.length > 0 ? new Icon({
            className: "fa fa-plus-circle fa-2x clickable bigMarginRight",
            // This mouseover stuff is compensating for the fact that when the onClick gets called
            // it's a problem that by then the text selection "might" have gotten lost. This can happen.
            onMouseOver: () => { S.quanta.selectedForTts = window.getSelection().toString(); },
            onMouseOut: () => { S.quanta.selectedForTts = null; },
            onClick: () => S.speech.appendSelOrClipboard(),
            title: "Text-to-Speech: Append more text from...\n\nText Area below, Selected Text, or Clipboard"
        }) : null;

        const speakAgainBtn = ast.ttsRan && S.speech.queuedSpeech?.length > 0 && !ast.mobileMode ? new Icon({
            className: "fa fa-refresh fa-2x bigMarginRight clickable",
            onClick: () => S.speech.speakText(null, false, 0),
            title: "Restart from the top"
        }) : null;

        const stopBtn = ast.speechSpeaking && !ast.mobileMode ? new Icon({
            className: "fa fa-stop fa-2x bigMarginRight clickable",
            onClick: () => S.speech.stopSpeaking(),
            title: "Stop Speaking Text"
        }) : null;

        const pauseBtn = ast.speechSpeaking && !ast.speechPaused && !ast.mobileMode ? new Icon({
            className: "fa fa-pause fa-2x bigMarginRight clickable",
            onClick: () => S.speech.pauseSpeaking(),
            title: "Pause Speaking Text"
        }) : null;

        const resumeBtn = ast.speechSpeaking && ast.speechPaused && !ast.mobileMode ? new Icon({
            className: "fa fa-play fa-2x bigMarginRight clickable",
            onClick: () => S.speech.resumeSpeaking(),
            title: "Resume Speaking Text"
        }) : null;

        let heading = null;
        if (ast.speechSpeaking) {
            heading = "Speaking...";
        }
        else if (ast.speechPaused) {
            heading = "Speech Paused.";
        }
        else {
            heading = "Ready to Speak";
        }

        let paraComps: CompIntf[];
        if (S.speech.queuedSpeech?.length > 0) {
            paraComps = [];
            let curDiv = new Divc({ className: "ttsPara" });

            let idx = 0;
            // scan each utterance
            S.speech.queuedSpeech.forEach(utter => {
                // if we hit a paragraph break
                if (utter === C.TTS_BREAK) {
                    if (curDiv.hasChildren()) {
                        paraComps.push(curDiv);
                    }
                    curDiv = new Divc({ className: "ttsPara" });
                }
                else {
                    const utterTrim = utter.trim();
                    const isQuote = utterTrim.startsWith("\"") && utterTrim.endsWith("\"");
                    curDiv.addChild(new Span(utter + "  ", {
                        onClick: this.spanClick, // <--- special function KNOWS how to work with no args
                        id: "tts" + idx,
                        className: "ttsSpan" + (S.speech.ttsHighlightIdx === idx ? " ttsHlt" : "") +
                            (isQuote ? " ttsQuote" : "")
                    }));
                }
                idx++;
            });

            if (curDiv.hasChildren()) {
                paraComps.push(curDiv);
            }
        }

        this.setChildren([
            this.headingBar = new TabHeading([
                new Div("Text-to-Speech", { className: "tabTitle" }),
                new Divc({ className: "float-end" }, [appendTextBtn, stopBtn, pauseBtn, resumeBtn, speakAgainBtn, speakBtn]),
                new Clearfix()
            ]),
            new FlexRowLayout([
                this.makeVoiceChooser(C.LOCALDB_VOICE_INDEX, true),
                S.speech.USE_VOICE2 ? this.makeVoiceChooser(C.LOCALDB_VOICE2_INDEX, false) : null,
                this.makeRateChooser(),
                new Checkbox("Text Input", { className: "bigMarginLeft" }, {
                    setValue: (checked: boolean) => dispatch("setTtsInput", s => {
                        if (!checked) {
                            TTSView.textAreaState.setValue("");
                        }
                        s.showTtsInputText = checked;
                    }),
                    getValue: (): boolean => getAs().showTtsInputText
                })
            ]),
            getAs().showTtsInputText ? new TextArea("Enter Text to Speak", {
                rows: 3
            }, TTSView.textAreaState) : null,
            paraComps?.length > 0
                ? new Divc({ className: "speechTxtArea" }, [
                    new Heading(4, heading, { className: "speechAreaTitle alert alert-primary" }),
                    ...paraComps
                ]) : null
        ]);
        return true;
    }

    makeVoiceChooser = (voiceKey: string, primaryVoice: boolean): Selection => {
        const data: any[] = [];
        let idx = 0;
        S.speech.getVoices()?.forEach(voice => {
            data.push({ key: "" + idx, val: voice.name + " " + voice.lang + (voice.default ? " -- DEFAULT" : "") });
            idx++;
        });

        return new Selection(null, primaryVoice ? "Voice" : "Quotation Voice",
            data, null, "selectVoiceDropDown", {
            setValue: (val: string) => {
                const voiceInt = parseInt(val);
                S.localDB.setVal(voiceKey, voiceInt);
                dispatch("ChangeSpeechVoice", s => {
                    if (primaryVoice) {
                        s.speechVoice = voiceInt;
                    }
                    else {
                        s.speechVoice2 = voiceInt;
                    }
                })
            },
            getValue: (): string => "" + (primaryVoice ? getAs().speechVoice : getAs().speechVoice2)
        });
    }

    makeRateChooser = (): Selection => {
        return new Selection(null, "Rate", [
            { key: "slowest", val: "Slowest" },
            { key: "slower", val: "Slower" },
            { key: "slow", val: "Slow" },
            { key: "normal", val: "Normal" },
            { key: "fast", val: "Fast" },
            { key: "faster", val: "Faster" },
            { key: "faster_1", val: "Faster +1" },
            { key: "fastest", val: "Fastest" }
        ], null, "rateChooserDropDown", {
            setValue: (val: string) => {
                S.localDB.setVal(C.LOCALDB_VOICE_RATE, val);
                dispatch("ChangeSpeechRate", s => {
                    s.speechRate = val;
                })
            },
            getValue: (): string => getAs().speechRate
        });
    }
}
