import { ProgressDlg } from "./dlg/ProgressDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

export class RpcUtil {
    rpcPath: string = null;
    rhost: string = null;
    logRpc: boolean = false;
    logRpcShort: boolean = false;
    timer: any = null;
    unauthMessageShowing: boolean = false;

    /*
    * We use this variable to determine if we are waiting for an ajax call, but the server also enforces that each
    * session is only allowed one concurrent call and simultaneous calls just "queue up".
    */
    rpcCounter: number = 0;
    waitCounter: number = 0;
    pgrsDlg: ProgressDlg = null;
    RPC_TIMER_INTERVAL = 1000;

    getRemoteHost = (): string => {
        if (this.rhost) {
            return this.rhost;
        }

        this.rhost = S.util.getParameterByName("rhost");
        this.rhost = this.rhost || window.location.origin;
        return this.rhost;
    }

    getRpcPath = (): string => {
        return this.rpcPath || (this.rpcPath = this.getRemoteHost() + "/api/");
    }

    /* Makes calls to server */
    rpc = <RequestType extends J.RequestBase, ResponseType extends J.ResponseBase> //
        (postName: string, postData: RequestType = null,
            background: boolean = false, allowErrorDlg: boolean = true): Promise<ResponseType> => {

        postData = postData || {} as RequestType;
        let reqPromise: Promise<ResponseType> = null;

        try {
            // todo-1: We *could* now get rid of this wrapper promise and always just return the
            // final thing that gets resolved.
            reqPromise = new Promise<ResponseType>((resolve, reject) => {
                if (this.logRpc) {
                    console.log("JSON-POST: [" + this.getRpcPath() + postName + "]" + S.util.prettyPrint(postData));
                }
                else if (this.logRpcShort) {
                    console.log("JSON-POST: [" + this.getRpcPath() + postName + "]");
                }

                // This setOverlay is turned back off
                if (!background) {
                    this.rpcCounter++;
                    S.quanta.setOverlay(true);
                }

                if (this.logRpc && !S.crypto.userSignature) {
                    console.warn("Request will have no signature.");
                }

                // const startTime = new Date().getTime();
                // console.log("fetch: " + this.getRpcPath() + postName + " Bearer: " + S.quanta.authToken);
                fetch(this.getRpcPath() + postName, {
                    method: "POST",
                    body: JSON.stringify(postData),
                    headers: {
                        "Content-Type": "application/json",
                        Bearer: S.quanta.authToken || "",
                        Sig: S.crypto.userSignature || ""
                    },
                    mode: "cors", // no-cors, *cors, same-origin
                    cache: "no-cache", // *default, no-cache, reload, force-cache, only-if-cached
                    credentials: "same-origin", // include, *same-origin, omit
                    referrerPolicy: "no-referrer"
                })
                    .then(async (res: any) => {
                        // Unauthorized refers to the session, and our session has likely timed out.
                        if (res.status === 401) {
                            console.warn("401 error for: " + postName);
                            reject({ response: res });
                            this.authFail();
                        }
                        else if (res.status !== 200) {
                            console.log("reject: " + this.getRpcPath() + postName + " Bearer: " + S.quanta.authToken);
                            reject({ response: res });
                        }
                        else {
                            return res.text();
                        }
                    })
                    .then((json: string) => {
                        /* if we did a reject above in the first 'then' we will get here with json undefined
                        so we ignore that */
                        if (json) {
                            // console.log("rpc: " + postName + " " + (new Date().getTime() - startTime) + "ms");
                            resolve(JSON.parse(json));
                        }
                        else {
                            reject(null);
                        }
                    })
                    .catch((error) => {
                        console.log("reject: " + this.getRpcPath() + postName + " Bearer: " + S.quanta.authToken);
                        reject(error);
                    });
            });
        } catch (ex) {
            S.util.logErr(ex, "Failed starting request: " + postName);
            if (!background) {
                S.quanta.setOverlay(false);
            }
            throw ex;
        }

        reqPromise.then((data: any) => this.rpcSuccess(data, background, postName))
            .catch((error: any) => this.rpcFail(error, background, allowErrorDlg, postName, postData));
        return reqPromise;
    }

    rpcSuccess = (data: any, background: boolean, postName: string) => {
        try {
            if (!background) {
                this.rpcCounter--;
                if (this.rpcCounter < 0) {
                    this.rpcCounter = 0;
                }
                this.progressInterval();
            }

            if (this.logRpc) {
                console.log("    JSON-RESULT: " + postName + "\n    JSON-RESULT-DATA: " +
                    S.util.prettyPrint(data));
            }

            if (!data.success && data.message) {
                // if we didn't just console log it then console log it now.
                if (!this.logRpc) {
                    console.error("FAILED JSON-RESULT: " + postName + "\n    JSON-RESULT-DATA: " +
                        S.util.prettyPrint(data));
                }
                S.util.showMessage(data.message, "Message");

                // get rid of message so it can't be shown again
                data.message = null;
                return;
            }
        } catch (ex) {
            S.util.logErr(ex, "Failed handling result of: " + postName);
            throw ex;
        }
        finally {
            if (!background) {
                S.quanta.setOverlay(false);
            }
        }
    }

    /**
     * We should only reach here when there's an actual failure to call the server, and is completely
     * separete from the server perhaps haveing an exception where it sent back an error.
     */
    rpcFail = (error: any, background: boolean, allowErrorDlg: boolean, postName: string, postData: any) => {
        try {
            if (!background) {
                this.rpcCounter--;
                if (this.rpcCounter < 0) {
                    this.rpcCounter = 0;
                }
                this.progressInterval();
            }
            let status = error.response ? error.response.status : "";
            const info = "Status: " + status + " message: " + error.message + " stack: " + error.stack;
            console.log("HTTP RESP [" + postName + "]: Error: " + info);

            // 401==Unauthorized
            if (error.response?.status === 401) {
                this.authFail();
                return;
            }

            let msg: string = `Failed: \nPostName: ${postName}\n`;
            msg += "PostData: " + S.util.prettyPrint(postData) + "\n";

            if (error.response) {
                msg += "Error Response: " + S.util.prettyPrint(error.response) + "\n";
            }

            msg += info;
            console.error("Request failed: msg=" + msg);

            status = error.response ? error.response.status : "";
            console.error("Failed: " + status + " " + (error.message || ""));

            if (!background && allowErrorDlg) {
                S.util.showMessage("Something went wrong. Try refreshing your browser.", "Warning", true);
            }
        } catch (ex) {
            S.util.logErr(ex, "Failed processing: " + postName);
            throw ex;
        }
        finally {
            if (!background) {
                S.quanta.setOverlay(false);
            }
        }
    }

    authFail = async () => {
        if (this.unauthMessageShowing) return;
        this.unauthMessageShowing = true;
        await S.util.showMessage("Unauthorized or Logged Out", "Warning");
        this.unauthMessageShowing = false;
    }

    incRpcCounter = () => {
        this.rpcCounter++;
        S.quanta.setOverlay(true);

        // incrementing waitCounter to 1 will make the progress indicator come up faster
        this.waitCounter = 1;
        this.progressInterval();
    }

    decRpcCounter = () => {
        this.rpcCounter--;
        S.quanta.setOverlay(false);
        if (this.rpcCounter < 0) {
            this.rpcCounter = 0;
        }
        this.progressInterval();
    }

    isRpcWaiting = (): boolean => {
        return this.rpcCounter > 0;
    }

    initRpcTimer = () => {
        // This timer is a singleton that runs always so we don't need to ever clear the timeout. Not a resource leak.
        this.timer = setInterval(this.progressInterval, this.RPC_TIMER_INTERVAL);
    }

    startBlockingProcess = () => {
        S.quanta.setOverlay(true);
    }

    stopBlockingProcess = () => {
        S.quanta.setOverlay(false);
    }

    progressInterval = () => {
        const isWaiting = S.rpcUtil.isRpcWaiting();
        if (isWaiting) {
            this.waitCounter++;
            if (this.waitCounter >= 3) {
                if (!this.pgrsDlg) {
                    const dlg = new ProgressDlg();
                    this.pgrsDlg = dlg;
                    this.pgrsDlg.open();
                }
            }
        } else {
            this.waitCounter = 0;
            if (this.pgrsDlg) {
                this.pgrsDlg.close();
                this.pgrsDlg = null;
            }
        }
    }
}
