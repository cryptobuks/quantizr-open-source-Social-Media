import { getAs, promiseDispatch } from "./AppContext";
import { Constants as C } from "./Constants";
import { S } from "./Singletons";
import { TTSView } from "./tabs/TTSView";

declare let webkitSpeechRecognition: any;
declare let SpeechRecognition: any;

export class SpeechEngine {
    // I'm disabling the dual voice thing for now
    public USE_VOICE2: boolean = false;

    // we keep this array here and not in AppState, because changes to this will never need to directly
    // trigger a DOM change.
    public queuedSpeech: string[] = null;

    private voices: SpeechSynthesisVoice[] = null;

    // this is a guess (and recommendation from online sources) at how long a sentence we can get away with
    // and still avoid the Chrome bug which cuts off long sentences. If sentence is short enough we push the
    // whole thing. There's a tradeoff here where you can set a large number for this (like well over 200), which causes
    // the ttsTimer (below) to activate a lot with "i think" can cause a slight speaker popping, --OR-- you can set this
    // value to like 200, and the popping will definitely not happen, but the sentence structure won't be perfect (meaning
    // the speaking voice may pause at awkward times every now and then)
    MAX_UTTERANCE_CHARS: number = 250;

    // add type-safety here (TS can find type easily)
    recognition: any = null;

    tts: SpeechSynthesis = window.speechSynthesis;
    ttsTimer: any = null;
    ttsIdx: number = 0;

    // we need this to have instantly fast (independent of AppState) way to detect
    // if we are tunning speech. ttsRunning means it's actively speaking now.
    ttsRunning: boolean = false;
    ttsSpeakingTime: number = 0;
    utter: SpeechSynthesisUtterance = null;

    speechActive: boolean = false;
    ttsHighlightIdx: number = 0;
    private callback: (text: string) => void;

    constructor() {
        this.initVoices();
    }

    // --------------------------------------------------------------
    // Speech Recognition
    // --------------------------------------------------------------

    initRecognition = () => {
        // already initialized, then return
        if (this.recognition) return;

        if (typeof SpeechRecognition === "function") {
            this.recognition = new SpeechRecognition();
        }
        else if (webkitSpeechRecognition) {
            // todo-2: fix linter rule to make this cleaner (the first letter upper case is the issue here)
            const WebkitSpeechRecognition = webkitSpeechRecognition;
            this.recognition = new WebkitSpeechRecognition();
        }

        if (!this.recognition) {
            S.util.showMessage("Speech recognition not available in your browser.", "Warning");
            return;
        }

        // This runs when the speech recognition service starts
        this.recognition.onstart = () => {
            // console.log("speech onStart.");
        };

        // This gets called basically at the end of every sentence as you're dictating content,
        // and paused between sentences, so we have to call start() again in here to start recording
        // another sentence
        this.recognition.onend = () => {
            // console.log("speech onEnd.");
            if (this.speechActive) {
                setTimeout(() => this.recognition.start(), 250);
            }
        };

        this.recognition.onspeechend = () => {
            // console.log("speech onSpeechEnd.");
        };

        // This runs when the speech recognition service returns result
        this.recognition.onresult = (event: any) => {
            const transcript = event.results[0][0].transcript;
            // const confidence = event.results[0][0].confidence;

            if (this.callback) {
                this.callback(transcript);
            }
        };
    }

    stopListening = () => {
        // if never initialized just return
        if (!this.recognition) return;
        this.initRecognition();
        this.recognition.stop();
        this.speechActive = false;
    }

    startListening = () => {
        this.initRecognition();
        this.recognition.start();
        this.speechActive = true;
    }

    setListenerCallback = (callback: (val: string) => void) => {
        this.callback = callback;
    }

    // --------------------------------------------------------------
    // Text to Speech
    // --------------------------------------------------------------

    initVoices = () => {
        // need to google "how to verify all voices loaded"
        const interval = setInterval(() => {
            this.getVoices();
            if (this.voices) {
                clearInterval(interval);
                console.log("tts loaded " + this.voices.length + " voices");
            }
            else {
                console.log("can't get voices yet from tts. Still trying.");
            }
        }, 1000);
    }

    speakSelOrClipboard = (allowUseEditField: boolean) => {
        if (allowUseEditField && TTSView.textAreaState.getValue()) {
            this.speakText(TTSView.textAreaState.getValue(), false);
        }
        else if (S.quanta.selectedForTts) {
            this.speakText(S.quanta.selectedForTts, false);
        }
        else {
            this.speakClipboard();
        }
    }

    // Append more text to buffer of what's being read.
    appendSelOrClipboard = async () => {
        let textToAdd: string = null;

        if (TTSView.textAreaState.getValue()) {
            textToAdd = TTSView.textAreaState.getValue();
            TTSView.textAreaState.setValue("");
        }
        else if (S.quanta.selectedForTts) {
            textToAdd = S.quanta.selectedForTts;
        }
        else {
            textToAdd = await (navigator as any)?.clipboard?.readText();
        }

        if (textToAdd) {
            this.appendTextToBuffer(textToAdd);
            await promiseDispatch("speechEngineStateChange", s => {
                setTimeout(() => {
                    this.highlightByIndex(this.ttsIdx);
                }, 250);
            });
        }
        else {
            S.util.showMessage("Neither Selected text nor Clipboard text is available.", "Warning");
        }
    }

    speakClipboard = async () => {
        if (!this.tts) return;

        const clipTxt = await (navigator as any)?.clipboard?.readText();
        if (clipTxt) {
            this.speakText(clipTxt);
        }
        else {
            S.util.showMessage("Clipboard text not available.", "Warning");
        }
    }

    speakText = async (text: string, selectTab: boolean = true, replayFromIdx: number = -1) => {
        const ast = getAs();

        // if currently speaking we need to shut down and wait 1200ms before trying to speak again,
        // but it would be better to use a listener or something to know precisely when it's ready
        // to start speaking again.
        if (ast.speechSpeaking) {
            this.stopSpeaking();
            setTimeout(() => {
                this.speakTextNow(text, selectTab, replayFromIdx);
            }, 1200);
        }
        else {
            this.speakTextNow(text, selectTab, replayFromIdx);
        }
    }

    jumpToIdx = (idx: number) => {
        if (this.queuedSpeech?.length > 1 && idx >= 0 && idx < this.queuedSpeech?.length) {

            this.stopSpeaking();
            this.highlightByIndex(idx);

            // Timeout to give the engine time to stop what it's doing. We could use PubSub, or a faster
            // polling to make this timer be a bit safer, in case 500ms isn't ok on slower machines.
            setTimeout(() => {
                this.speakTextNow(null, false, idx);
            }, 250);
        }
    }

    // you can pass null, and this method will repeat it's current text.
    speakTextNow = async (text: string, selectTab: boolean = true, replayFromIdx: number = -1) => {
        if (!this.tts || (!text && replayFromIdx === -1)) return;
        this.ttsRunning = true;
        this.createTtsTimer();

        if (selectTab) {
            S.tabUtil.selectTab(C.TAB_TTS);
        }

        // only because speech has had bugs over the years and one bug report I saw claimed putting the call
        // in a timeout helped, I'm doing that here, because I had a hunch this was best even before I saw someone
        // else make the claim.
        setTimeout(async () => {
            this.getVoices();
            if (!this.voices) {
                console.warn("Voices not loaded. Can't speak text");
                return;
            }

            // Just use 0 idx if the one passed in is out of range somehow.
            if (replayFromIdx >= this.queuedSpeech?.length) {
                replayFromIdx = 0;
            };

            if (replayFromIdx === -1) {
                this.queuedSpeech = [];
                this.appendTextToBuffer(text);
                this.ttsIdx = 0;
            }
            else {
                this.ttsIdx = replayFromIdx;
            }

            await promiseDispatch("speechEngineStateChange", s => {
                s.speechPaused = false;
                s.speechSpeaking = true;
                s.ttsRan = true;
            });

            this.queuedSpeech = this.queuedSpeech.filter(p => p.length > 0);
            if (this.queuedSpeech.length === 0) {
                this.queuedSpeech = null;
                return;
            }

            let utter: SpeechSynthesisUtterance = null;

            /* NOTE: This utterFunc gets used over and over in a daisy chain type way to process the
            next utterance every time the previous one completes. */
            const utterFunc = () => {
                if (!this.ttsRunning || !this.queuedSpeech) return;
                const ast = getAs();

                // If we're out of stuff to speak
                if (this.ttsIdx >= this.queuedSpeech.length) {
                    this.stopSpeaking();
                    return;
                }

                // If we have more stuff to speak
                if (this.ttsIdx < this.queuedSpeech.length) {
                    let sayThis = this.queuedSpeech[this.ttsIdx];

                    // if this is a paragraph break skip it, with idx++
                    while (sayThis === C.TTS_BREAK) {
                        // no more left?
                        if (++this.ttsIdx >= this.queuedSpeech.length) {
                            this.stopSpeaking();
                            return;
                        }

                        // keep going, with this sayThis.
                        sayThis = this.queuedSpeech[this.ttsIdx];
                    }

                    // Let's rip out all the hashtags and at symbols mainly just so we can read
                    // text full of hashtags and have it sound good.
                    sayThis = sayThis.replace("#", " ");

                    // replace backquote or else the engine will pronounce the actual word 'backquote' which we of courose
                    // do not want.
                    sayThis = sayThis.replace("`", "\"");

                    utter = new SpeechSynthesisUtterance(sayThis);

                    const isQuote = sayThis.startsWith("\"");
                    if (isQuote && this.USE_VOICE2 && ast.speechVoice2 >= 0) {
                        const voices = this.getVoices();
                        utter.voice = voices[(ast.speechVoice2 < voices.length ? ast.speechVoice2 : 0)];
                    }
                    else if (ast.speechVoice >= 0) {
                        const voices = this.getVoices();
                        utter.voice = voices[(ast.speechVoice < voices.length ? ast.speechVoice : 0)];
                    }

                    if (ast.speechRate) {
                        utter.rate = this.parseRateValue(ast.speechRate);
                    }

                    utter.onend = () => {
                        this.ttsSpeakingTime = 0;
                        this.utter = null;
                        if (!this.ttsRunning) return;
                        utterFunc();
                    }

                    if (!this.ttsRunning) return;
                    // console.log("SPEAK[" + sayThis.length + "]: " + sayThis);
                    this.ttsSpeakingTime = 0;
                    this.utter = utter;
                    this.highlightByIndex(this.ttsIdx);
                    this.ttsIdx++;
                    this.tts.speak(utter);
                }
            };
            this.ttsRunning = true;
            // Get started by uttering idx=0, and the rest of the sentences will follow
            // in a chain reaction every time utterFunc gets called via the 'onend' listener of
            // the most recently completed utterance
            utterFunc();
        }, 100);
    }

    createTtsTimer = () => {
        // create timer that runs forever and fixes the Chrome bug whenever speech has been
        // running more than ten seconds.
        if (!this.ttsTimer) {
            const interval = 1000;
            // https://stackoverflow.com/questions/21947730/chrome-speech-synthesis-with-longer-texts
            this.ttsTimer = setInterval(() => {
                if (!this.ttsRunning) return;
                const ast = getAs();
                if (ast.speechSpeaking && !ast.speechPaused) {
                    this.ttsSpeakingTime += interval;
                    if (this.ttsSpeakingTime > 10000) {
                        this.ttsSpeakingTime = 0;

                        // todo-1: need to research this "fix" even more, because it appears even
                        // pausing vor 10 seconds makes the TTS engine break, and if the only fix
                        // to that breaking is a resume again, that means we simply CANNOT use pause.
                        // Must stop and restart to simulate a pause
                        this.tts.pause();
                        this.tts.resume();
                    }
                }
            }, interval);
        }
    }

    removeHighlight = () => {
        if (this.ttsHighlightIdx !== -1) {
            const lastElm = document.getElementById("tts" + this.ttsHighlightIdx);
            lastElm?.classList.remove("ttsHlt");
            this.ttsHighlightIdx = -1; // keep this consistent just for best practice
        }
    }

    highlightByIndex = (idx: number) => {
        // remove any previous highlighting
        this.removeHighlight();

        const elm = document.getElementById("tts" + idx);
        if (elm) {
            elm.classList.add("ttsHlt");
        }
        this.ttsHighlightIdx = idx;
    }

    parseRateValue = (rate: string) => {
        switch (rate) {
            case "slowest":
                return 0.7;
            case "slower":
                return 0.8;
            case "slow":
                return 0.9;
            case "normal":
                return 1;
            case "fast":
                return 1.1;
            case "faster":
                return 1.2;
            case "faster_1":
                return 1.3;
            case "fastest":
                return 1.4;
            default:
                return 1;
        }
    }

    getVoices = () => {
        if (this.voices) return this.voices;
        this.voices = this.tts.getVoices();
        this.tts.onvoiceschanged = () => {
            this.voices = this.tts.getVoices();
            console.log("VoicesChanged ran. Now we have " + this.voices?.length + " voices.");
        };
    }

    preProcessText = (text: string): string => {
        if (!text) return;
        // engine will SAY the 'quote' if you leave this here.
        text = text.replace(".\"", ".");
        text = text.replace(".'", ".");

        text = text.replace("!\"", "!");
        text = text.replace("!'", "!");

        text = text.replace("?\"", "?");
        text = text.replace("?'", "?");

        // need to create a list to iterate thru for these.
        text = text.replace("Rep.", "Rep");
        return text;
    }

    // as a last resort we just break string at spaces to create an array of unser MAX_UTTERANCE_CHARS
    // chunks of text
    fragmentBySpaces = (text: string): string[] => {
        const ret: string[] = [];
        const ast = getAs();
        const maxChars = this.MAX_UTTERANCE_CHARS * this.parseRateValue(ast.speechRate);

        // first split into 'words'. All things separated by spaces.
        const words = text.split(/[ ]+/g);

        // scan each word appendingn to frag until it gets too long and then
        // adding to ret
        let frag = "";
        words?.forEach(word => {
            if (frag.length + word.length < maxChars) {
                frag += " " + word;
            }
            else {
                ret.push(frag.trim());
                frag = word;
            }
        });

        if (frag.length > 0) {
            ret.push(frag.trim());
        }

        return ret;
    }

    appendTextToBuffer = (text: string) => {
        if (!text) return;
        text = this.preProcessText(text);
        const ast = getAs();
        const maxChars = this.MAX_UTTERANCE_CHARS * this.parseRateValue(ast.speechRate);

        // first split into sentences.
        // 1) Match a single character present in [\n\r]
        // 2) '+' matches the previous token between one and unlimited times, as many
        //    times as possible, giving back as needed (greedy)
        const paragraphs = text.split(/[\n\r]+/g);

        paragraphs?.forEach(para => {
            if (para.length < 3) return;

            // if entire paragraph can fit
            if (para.length < maxChars) {
                this.pushTextToQueue(para);
            }
            else {
                this.fragmentizeSentencesToQueue(para);
            }

            // This is a harmless trick/hack where we avoid a significant complexity jump by doing something
            // slightly anti-patternish, but is good in this case, for now
            this.queuedSpeech.push(C.TTS_BREAK);
        });
    }

    splitByQuotations = (text: string): string[] => {
        text = text.replace("“", "\"");
        text = text.replace("”", "\"");
        const quoteCount = S.util.countChars(text, "\"");

        let ret: string[] = null;
        if (quoteCount % 2 === 0) {
            ret = [];
            let inQuote = false;

            // Split by quote char, and also return the delimiters (using lookback).
            const chunk = text.split(/(?=["]+)|(?<=["]+)/g);
            chunk?.forEach(frag => {
                if (frag === "\"") {
                    // finishing a quote
                    if (inQuote) {
                        inQuote = false;
                        // wrap previous string in quotes, because this is the correct text AND because the
                        // engine will be detecting that during playback to use quoted voice.
                        if (ret.length > 0) {
                            ret[ret.length - 1] = "\"" + ret[ret.length - 1] + "\"";
                        }
                    }
                    // starting a quote
                    else {
                        inQuote = true;
                    }
                }
                else {
                    ret.push(frag);
                }
            });
        }
        return ret;
    }

    // The Chrome Speech engine will stop working unless you send it relatively short chunks of text. It's basically
    // a time related thing where if it speaks for more than about 10 seconds at a time it hangs.
    // See the setInterval function in this class for more on the tradeoffs/workarounds related to this.
    fragmentizeSentencesToQueue = (text: string) => {
        const ast = getAs();
        const maxChars = this.MAX_UTTERANCE_CHARS * this.parseRateValue(ast.speechRate);

        // This is a dirty but clever hack to fix lots of initials like (J.F.K.)
        // and make them not do any sentence breaks there.
        for (const char of "ABCDEFGHIJKLMNOPQRSTUVWXYZ") {
            text = text.replace(char + ".", char + " ");
        }

        // first split into sentences.
        // DO NOT DELETE (this example is how to NOT return punctuation)
        // const sentences = text.split(/[.!?;]+/);
        // This REGEX splits by the delimiters "." and includes the delimiters in the search results.
        // NOTE: This regex for "split()" is supposed to return only the "split points" and not the text.
        // V1: const sentences = text.split(/(?=[.!?;]+)|(?<=[.!?;]+)/g);
        // V2: MUCH BETTER! from here:
        // https://stackoverflow.com/questions/25735644/python-regex-for-splitting-text-into-sentences-sentence-tokenizing
        // ------------------------------------
        // ANALYSIS BY REGEX (below) generated by https://regex101.com:
        //
        // Negative Lookbehind (?<!\w\.\w.)
        // Assert that the Regex below does not match

        // \w matches any word character (equivalent to [a-zA-Z0-9_])
        // \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
        // \w matches any word character (equivalent to [a-zA-Z0-9_])
        // . matches any character (except for line terminators)
        // Negative Lookbehind (?<![A-Z][a-z]\.)
        // Assert that the Regex below does not match

        // Match a single character present in the list below [A-Z]
        // A-Z matches a single character in the range between A (index 65) and Z (index 90) (case sensitive)
        // Match a single character present in the list below [a-z]
        // a-z matches a single character in the range between a (index 97) and z (index 122) (case sensitive)
        // \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
        // Positive Lookbehind (?<=\.|\?|\!)
        // Assert that the Regex below matches
        // 1st Alternative \.
        // \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
        // 2nd Alternative \?
        // \? matches the character ? with index 6310 (3F16 or 778) literally (case sensitive)
        // 3rd Alternative \!
        // \! matches the character ! with index 3310 (2116 or 418) literally (case sensitive)
        // \s matches any whitespace character (equivalent to [\r\n\t\f\v ])
        // Global pattern flags
        // g modifier: global. All matches (don't return after first match)
        // m modifier: multi line. Causes ^ and $ to match the begin/end of each line (not only begin/end of string)
        // ------------------------------------
        const sentences = text.split(/(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=\.|\?|\!)\s/gm);

        // scan each sentence
        sentences?.forEach(sentence => {

            // Handle Punctuation
            // it's harmless to always tack on a single character to the most recent chunk, and
            // this IS how we get punctuation in at end of sentences (.!?;)
            if (sentence.length === 1) {
                if (this.queuedSpeech.length > 0) {
                    this.queuedSpeech[this.queuedSpeech.length - 1] += sentence;
                    return;
                }
                // Yes right here, we fall thru and don't return. Not a bug. Don't want to loose any chars.
            }

            // if this sentence itself is short enough just add to queue
            if (sentence.length < maxChars) {
                this.pushTextToQueue(sentence);
            }
            // Otherwise we have to break the sentence apart, so we break by commas first
            else {
                const fragments = sentence.split(/[,]+/g);
                let fragMerge = "";
                fragments?.forEach(frag => {
                    // handle the comma delimiter
                    if (sentence.length === 1) {

                        // if we're building a fragMerge, add delimiter in
                        if (fragMerge.length > 0) {
                            fragMerge += frag;
                        }
                        // otherwise et put the comma onto the end of the last known content
                        else if (this.queuedSpeech.length > 0) {
                            this.queuedSpeech[this.queuedSpeech.length - 1] += sentence;
                            return;
                        }
                    }
                    // if we can fit more onto the fragMerge then append.
                    else if (fragMerge.length + frag.length < maxChars) {
                        fragMerge += frag;
                    }
                    // if frag is short enough to make the new fragMerge do that.
                    else if (frag.length < maxChars) {
                        if (fragMerge) {
                            this.pushTextToQueue(fragMerge);
                        }
                        fragMerge = frag;
                    }
                    // else 'frag' would would make fragMerge too large, so we commit the current
                    // fragMerge to the queue, first, and then queue by breaking the sentence by words.
                    else {
                        if (fragMerge) {
                            this.pushTextToQueue(fragMerge);
                        }
                        fragMerge = "";
                        this.queuedSpeech = this.queuedSpeech.concat(this.fragmentBySpaces(frag));
                    }
                });

                // push whatever was left.
                if (fragMerge) {
                    this.pushTextToQueue(fragMerge);
                }
            }
        });
    }

    // We have this push function basically so we can split up quotations. This splitting is what allows
    // us to switch voices if we went to (for quotations) but is also a way to keep the utterances as short
    // ass possible, which is needed to help Chrome not hang.
    pushTextToQueue = (text: string) => {
        if (!this.USE_VOICE2) {
            this.queuedSpeech.push(text);
            return;
        }

        const textWithQuotes = this.splitByQuotations(text);
        if (textWithQuotes) {
            this.queuedSpeech = this.queuedSpeech.concat(textWithQuotes)
        }
        else {
            this.queuedSpeech.push(text);
        }
    }

    // We manage 'paused & speaking' state ourselves rather than relying on the engine to have those
    // states correct, because TRUST ME at least on Chrome the states are unreliable.
    // If you know you're about to speak some new text you can pass in that text to update screen ASAP
    stopSpeaking = async () => {
        if (!this.tts) return;
        this.ttsRunning = false;
        if (this.utter) {
            this.utter.volume = 0;
        }
        this.removeHighlight();
        this.ttsSpeakingTime = 0;

        await promiseDispatch("speechEngineStateChange", s => {
            s.speechPaused = false;
            s.speechSpeaking = false;
        });
        this.tts.cancel();
    }

    // Using "tts.cancel()" instead of "tts.pause()" to work around Chrome Bug
    pauseSpeaking = async () => {
        if (!this.tts) return;
        this.ttsRunning = false;

        await promiseDispatch("speechEngineStateChange", s => {
            s.speechPaused = true;
        });
        this.tts.cancel();
    }

    // Using "jumpToIdx()" instead of "tts.resume()" to work around Chrome Bug
    resumeSpeaking = async () => {
        // we use ttsIdx-1 as out starting point because this index is always kind of 'pre-advanced'
        // to the next utterance once a given utterance is stated.
        this.jumpToIdx(this.ttsIdx - 1);
    }

    // DO NOT DELETE:
    // These pauseSpeaking() and resumeSpeaking() functions works, until the "10 second Chrome Bug" but breaks
    // it again in chrome. So if Chrome ever fixes their bug these two methods will be able to work because they
    // ARE correct (if Chrome worked)
    // pauseSpeaking = async () => {
    //     if (!this.tts) return;
    //     this.ttsRunning = false;
    //     if (this.utter) {
    //         this.utter.volume = 0;
    //     }
    //     await promiseDispatch("speechEngineStateChange", s => {
    //         s.speechPaused = true;
    //     });
    //     this.tts.pause();
    // }
    // DO NOT DELETE:
    // resumeSpeaking = async () => {
    //     if (!this.tts) return;
    //     this.ttsRunning = true;
    //     if (this.utter) {
    //         this.utter.volume = 1;
    //     }
    //     await promiseDispatch("speechEngineStateChange", s => {
    //         s.speechPaused = false;
    //         s.speechSpeaking = true;
    //     });
    //     this.tts.resume();
    // }
}
