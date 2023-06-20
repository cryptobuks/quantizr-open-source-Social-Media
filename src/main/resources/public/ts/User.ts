import { dispatch, getAs, promiseDispatch } from "./AppContext";
import { Constants as C } from "./Constants";
import { ConfirmDlg } from "./dlg/ConfirmDlg";
import { FriendsDlg } from "./dlg/FriendsDlg";
import { LoginDlg } from "./dlg/LoginDlg";
import { SignupDlg } from "./dlg/SignupDlg";
import { UserProfileDlg } from "./dlg/UserProfileDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

export class User {
    closeAccount = async () => {
        let dlg = new ConfirmDlg("Are you sure you want to close your account?", "Close Account");
        await dlg.open();
        if (!dlg.yes) {
            return;
        }

        dlg = new ConfirmDlg("Are you sure? Your data will be deleted and can never be recovered.", "Close Account");
        await dlg.open();
        if (dlg.yes) {
            await S.localDB.clearStores();
            await S.rpcUtil.rpc<J.CloseAccountRequest, J.CloseAccountResponse>("closeAccount");

            /* Remove warning dialog to ask user about leaving the page */
            window.onbeforeunload = null;

            /* reloads browser with the query parameters stripped off the path */
            window.location.href = window.location.origin;
        }
    }

    /*
     * for testing purposes, I want to allow certain users additional privileges. A bit of a hack because it will go
     * into production, but on my own production these are my "testUserAccounts", so no real user will be able to
     * use these names
     */
    isTestUserAccount = (): boolean => {
        const lcUserName = getAs().userName.toLowerCase();
        return lcUserName === "adam" || //
            lcUserName === "bob" || //
            lcUserName === "cory" || //
            lcUserName === "dan";
    }

    initLoginState = async () => {
        const loginState: string = await S.localDB.getVal(C.LOCALDB_LOGIN_STATE);
        const usr = await S.localDB.getVal(C.LOCALDB_LOGIN_USR);
        const pwd = await S.localDB.getVal(C.LOCALDB_LOGIN_PWD);

        const callUsr: string = usr || "";
        const callPwd: string = pwd || "";

        /* if we have *known* state as logged out, then do nothing here */
        if (loginState === "0" || !callUsr) {
            if (!S.quanta.config.initialNodeId) {
                S.quanta.config.initialNodeId = ":home";
            }
            return;
        }

        // console.log("refreshLogin: user=" + usr + " pwd=" + pwd);
        const usingCredentials: boolean = usr && pwd;

        try {
            await S.quanta.initKeys(callUsr);

            const res = await S.rpcUtil.rpc<J.LoginRequest, J.LoginResponse>("login", {
                userName: callUsr,
                password: callPwd,
                tzOffset: new Date().getTimezoneOffset(),
                dst: S.util.daylightSavingsTime,
                sigKey: S.crypto.sigKey,
                asymEncKey: S.crypto.asymEncKey,
                nostrNpub: S.nostr.npub,
                nostrPubKey: S.nostr.pk
            }, false, true);
            S.quanta.authToken = res.authToken;

            if (res && !res.success) {
                await S.localDB.setVal(C.LOCALDB_LOGIN_STATE, "0");
                if (!S.quanta.config.initialNodeId) {
                    S.quanta.config.initialNodeId = ":home";
                }
            }

            if (usingCredentials) {
                // Note: If user entered wrong case-sentitivity string on login dialog they can still login
                // but this res.userName however will have the correct name (case-sensitive) here now.
                await this.loginResponse(res, res.userProfile.userName, callPwd, false);
            } else {
                if (res.success) {
                    S.util.setInitialStateVars(res);
                }
            }
        }
        catch (e) {
            S.util.logErr(e);
            await S.localDB.setVal(C.LOCALDB_LOGIN_STATE, "0");
            if (!S.quanta.config.initialNodeId) {
                S.quanta.config.initialNodeId = ":home";
            }
        }
    }

    logout = async () => {
        /* Remove warning dialog to ask user about leaving the page */
        window.onbeforeunload = null;

        // set user to know they're logged out
        await S.localDB.setVal(C.LOCALDB_LOGIN_STATE, "0");

        // set anon user to know they're logged out
        S.localDB.setUser(J.PrincipalName.ANON);
        await S.localDB.setVal(C.LOCALDB_LOGIN_STATE, "0");

        if (getAs().isAnonUser) {
            return;
        }

        S.quanta.loggingOut = true;
        try {
            await S.rpcUtil.rpc<J.LogoutRequest, J.LogoutResponse>("logout");
        }
        finally {
            this.logoutResponse();
        }
    }

    logoutResponse = () => {
        S.push.close();
        S.quanta.authToken = null;
        S.quanta.userName = null;
        window.location.href = window.location.origin;
    }

    showUserProfileByNostrKey = (identity: string) => {
        const dlg = new UserProfileDlg(null, identity);
        dlg.open();
    }

    loginResponse = async (res: J.LoginResponse, usr: string, pwd: string, calledFromLoginDlg: boolean) => {
        if (S.util.checkSuccess("Login", res)) {

            // if login was successful and we're an authenticated user
            if (usr !== J.PrincipalName.ANON) {

                // Setting CREDS on 'anon' user means when user comes back to this page it automatically can log them in
                S.localDB.setUser(J.PrincipalName.ANON);
                await this.setLoginVars(usr, pwd, "1");

                // Setting CREDS after switching DB user
                S.localDB.setUser(usr);
                await this.setLoginVars(usr, pwd, "1"); // <-- note this is NOT a redundant line.

                S.quanta.userName = usr;
                console.log("Logged in as: " + usr);

                setTimeout(() => {
                    S.push.init(res.authToken);

                    // NOTE: All these are async methods, but we don't use 'await' because we can let them
                    // execute in parallel and not wait for any of them to complete before any of the others.
                    if (usr !== J.PrincipalName.ADMIN) {
                        this.checkMessages();
                    }
                    S.util.loadBookmarks();
                }, 500);

                // todo-1: technically this delay is a bit of a hack because we really need a way to be SURE
                // the main app layout has already loaded before we even try to resume editing.
                setTimeout(() => {
                    S.util.resumeEditingOfAbandoned();
                }, 1500);
            }

            await S.util.setInitialStateVars(res);

            /* if we know the server already failed to get the content requested on the url then
            default to main tab (tree) and set it up to display an error */
            if (S.quanta.config.urlIdFailMsg) {
                await promiseDispatch("setAccessFailed", s => {
                    s.activeTab = C.TAB_MAIN;
                });

                // special case when we fail to access a nostr node we attempt to load it from the client side
                if (!S.quanta.config.loadNostrId) {
                    return;
                }
            }
        } else {
            console.log("LocalDb login failed.");

            // if we tried a login and it wasn't from a login dialog then just blow away the login state
            // so that any kind of page refresh is guaranteed to just show login dialog and not try to login
            S.localDB.setVal(C.LOCALDB_LOGIN_STATE, "0");

            if (!calledFromLoginDlg) {
                this.userLogin();
            }
        }
    }

    setLoginVars = async (usr: string, pwd: string, loginState: string) => {
        if (usr) {
            await S.localDB.setVal(C.LOCALDB_LOGIN_USR, usr);
        }
        if (pwd) {
            await S.localDB.setVal(C.LOCALDB_LOGIN_PWD, pwd);
        }
        await S.localDB.setVal(C.LOCALDB_LOGIN_STATE, loginState);
    }

    checkMessages = async () => {
        const res = await S.rpcUtil.rpc<J.CheckMessagesRequest, J.CheckMessagesResponse>("checkMessages", null, true);
        if (res) {
            dispatch("SetNewMessageCount", s => {
                s.myNewMessageCount = res.numNew;
            });
        }
    }

    queryUserProfile = async (userId: string) => {
        const res = await S.rpcUtil.rpc<J.GetUserProfileRequest, J.GetUserProfileResponse>("getUserProfile", {
            userId,
            nostrPubKey: null
        });

        if (res?.userProfile) {
            await promiseDispatch("SetUserProfile", s => {
                s.userProfile = res.userProfile;
            });
        }
    }

    userLogin = async () => {
        new LoginDlg().open();
    }

    userSignup = () => {
        new SignupDlg().open();
    }

    showUsersList = (node: J.NodeInfo) => {
        const friendsDlg = new FriendsDlg("Mentions & Shares", node.id, true);
        friendsDlg.open();
    }
}
