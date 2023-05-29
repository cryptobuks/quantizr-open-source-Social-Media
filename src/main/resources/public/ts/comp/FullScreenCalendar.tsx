import { S } from "../Singletons";
import { Main } from "./Main";
import React, { ReactNode, createElement } from "react";
import { dispatch, getAs } from "../AppContext";

/* ========= WARNING =========
Do not re-arrange these imports because fullcalendar will have a problem if you do!!! It needs to load them in this order.
*/
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin, { DateClickArg } from "@fullcalendar/interaction";

export class FullScreenCalendar extends Main {
    static lastClickTime: Date;

    constructor() {
        super();
        this.domUpdateEvent = this.domUpdateEvent.bind(this);
    }

    override compRender = (): ReactNode => {
        const state = getAs();
        const nodeId = state.fullScreenConfig.nodeId;
        const node = S.nodeUtil.findNode(nodeId);

        if (!node) {
            console.log("Can't find nodeId " + nodeId);
        }

        return this.tag("div", {
            className: "marginAll"
        }, [
            createElement(FullCalendar, {
                plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
                headerToolbar: {
                    left: "prev,next today,weekendsEventButton,addEventButton,closeCalendarButton",
                    center: "title",
                    right: "dayGridMonth,timeGridWeek,timeGridDay"
                },

                // WARNING: setting window sizes, or even this aspect ratio causes a bug when the user resizes the window
                // and this bug completely disables the app.
                // aspectRatio: 2.4,

                initialDate: FullScreenCalendar.lastClickTime || new Date(),
                initialView: "dayGridMonth",
                editable: false,
                selectable: false,
                selectMirror: true,
                dayMaxEvents: true,
                weekends: state.calendarShowWeekends,
                initialEvents: state.calendarData,
                dateClick: this.dateClick,
                // select: this.handleDateSelect,
                eventContent: renderEventContent,
                eventClick: this.handleEventClick,
                // eventsSet: {this.handleEvents}

                customButtons: {
                    addEventButton: {
                        text: "add",
                        click: () => {
                            FullScreenCalendar.lastClickTime = FullScreenCalendar.lastClickTime || new Date();
                            S.edit.addCalendarEntry(FullScreenCalendar.lastClickTime.getTime());
                        }
                    },
                    closeCalendarButton: {
                        text: "Close",
                        click: () => {
                            S.nav.closeFullScreenViewer();
                        }
                    },
                    weekendsEventButton: {
                        text: "weekend",
                        click: () => {
                            dispatch("Action_CalendarToggleWeekends", s => {
                                s.calendarShowWeekends = !state.calendarShowWeekends;
                                return s;
                            });
                        }
                    }
                }
            }, null)
        ]);
    }

    dateClick = (dateClick: DateClickArg): void => {
        FullScreenCalendar.lastClickTime = dateClick.date;
        const calendarApi = dateClick.view.calendar;
        calendarApi.changeView("timeGridDay");
        calendarApi.gotoDate(dateClick.date);
    }

    handleDateSelect = (selectInfo: any) => {
    }

    handleEventClick = (clickInfo: any) => {
        S.edit.runEditNode(null, clickInfo.event.id, true, false, true, null, false);
    }

    override domUpdateEvent = (): void => {
        // #DEBUG-SCROLLING
        S.view.docElm.scrollTop = 0;
    }
}

function renderEventContent(eventContent: any) {
    return (
        <>
            <b>{eventContent.timeText} - </b>
            <i>{eventContent.event.title}</i>
        </>
    );
}
