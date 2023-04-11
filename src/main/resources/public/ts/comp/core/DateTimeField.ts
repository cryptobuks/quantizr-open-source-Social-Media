import { S } from "../../Singletons";
import { Validator } from "../../Validator";
import { DateField } from "./DateField";
import { Span } from "./Span";
import { TextField } from "./TextField";
import { TimeField } from "./TimeField";

export class DateTimeField extends Span {
    dateState: Validator = new Validator();
    timeState: Validator = new Validator();

    // dateTimeState holds the string value of the date number milliseconds
    constructor(private dateTimeState: Validator, private durationState?: Validator, private showTime?: boolean) {
        super(null);
        this.attribs = {
            ...this.attribs, ...{
                className: "input-group marginRight"
            }
        };

        this.dateState.v.stateTranslator = (s: any): any => {
            try {
                const newDate = new Date(s.value + " " + this.timeState.getValue() + ":00");
                this.dateTimeState.setValue("" + newDate.getTime());
            }
            catch (e) {
                console.log("ignoring date error");
            }
            return s;
        };

        this.timeState.v.stateTranslator = (s: any): any => {
            try {
                const newDate = new Date(this.dateState.getValue() + " " + s.value + ":00");
                this.dateTimeState.setValue("" + newDate.getTime());
            }
            catch (e) {
                console.log("ignoring time error");
            }
            return s;
        };

        const dateTimeStr: string = dateTimeState.getValue();
        let dateTime: Date;

        try {
            if (!dateTimeStr) {
                dateTime = S.util.addTimezoneOffset(new Date(), -1);
            }
            else {
                dateTime = new Date(parseInt(dateTimeStr));
                dateTime = S.util.addTimezoneOffset(dateTime, -1);
            }
        }
        catch (e) {
            S.util.logErr(e, "Unable to parse: " + dateTimeStr);
            dateTime = S.util.addTimezoneOffset(new Date(), -1);
        }

        const isoStr = dateTime.toISOString();
        const dateStr = isoStr.substring(0, 10);
        this.dateState.setValue(dateStr);

        let hourStr: string = "" + dateTime.getUTCHours();
        if (hourStr.length < 2) {
            hourStr = "0" + hourStr;
        }

        let minStr: string = "" + dateTime.getUTCMinutes();
        if (minStr.length < 2) {
            minStr = "0" + minStr;
        }

        const timeStr = hourStr + ":" + minStr;
        this.timeState.setValue(timeStr);
    }

    override preRender(): boolean {
        this.setChildren([
            new DateField(this.dateState),
            this.showTime ? new TimeField(this.timeState, "marginLeft") : null,
            this.durationState ? new TextField({
                // NO LABEL!: We have no room at top for a label because we're lining up with the rest
                // of these components vertically which also have no labels.
                // label: "HH:MM",
                placeholder: "Duration...",
                inputClass: "durationTypeInput",
                outterTagName: "span",
                val: this.durationState
            }) : null
        ]);
        return true;
    }
}
