/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppContext, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import * as admin from './admindashboard';
import {createMemo, createResource, createSignal, ErrorBoundary, For, Show, useContext} from 'solid-js';
import {BoxInside, BoxWithHeader, InnerElement, OuterBox, RowOf, TextCopyRow, TextInputGroup, TextListRow} from './boxes';
import { OrError, orErrorSignal } from './utils';
import { z } from 'zod';
import {createStore, unwrap} from "solid-js/store";

// Safari (perhaps all webkit browsers?) does not currently support this without a polyfill
import { Temporal } from 'temporal-polyfill'
import {DELETE, Icon} from "./icons";
import {Delete} from "./HeaderButtons";

const root = document.getElementById('root');

function TokensList() {
    const context = useContext(AppContext);

    const [namespaces] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/namespaces/${context!.identity.id}/list`, api.Namespaces);
    });
    const [repositories] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/repositories`, z.string().array());
    });

    const [tokens, { refetch }] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/tokens`, api.AccessToken.array());
    });

    // These will always be displayed first
    const [createdTokens, setCreatedTokens] = createStore([] as api.AccessToken[]);

    const tokensToDisplay = createMemo(() => {
        const keys = createdTokens.map((i) => i.key);
        const tokensOut: api.AccessToken[] = [];
        for (const token of createdTokens) {
            tokensOut.push(token);
        }
        if (tokens()) {
            for (const token of (tokens()!)) {
                if (!(token.key in keys)) {
                    tokensOut.push(token);
                }
            }
        }
        return tokensOut;
    });

    const [ toCreate, setToCreate ] = createStore(api.newTokenRequest(context!.identity.id));
    const [ status, setStatus ] = orErrorSignal();

    return <>
        <ErrorBoundary fallback={(error) => {
            console.error(error);
            return (<OuterBox>
                <div class="text-red-600 p-5">
                    Error: {error.message}
                </div>
            </OuterBox>)
        }}>
            <Show when={namespaces() && repositories()}><OuterBox>
                <div class="shadow-sm"><TextInputGroup type="text" accessor={() => toCreate.name} setter={(value: string) => {
                    setToCreate("name", value);
                }} placeholder="Create access token" submit="Create" onsubmit={async () => {
                    const current: api.AccessTokenRequest = { ...unwrap(toCreate) }

                    if (!api.validateAccessTokenRequest(current, setStatus, namespaces()!.map((it) => it.namespace), repositories()!)) {
                        return;
                    }

                    setToCreate(api.newTokenRequest(context!.identity.id));
                    setStatus({ status: "working" });

                    try {
                        const createdToken = await api.postAndListenJSON("/dashboard/api/tokens", current, api.AccessTokenRequest, api.AccessToken);
                        setCreatedTokens(createdTokens.concat([createdToken]));
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                    }
                    setStatus({ status: "ok" });
                }} /></div>
                <BoxInside>
                    <InnerElement>
                        <div class="flex flex-col gap-2">
                            <div class="grid grid-cols-[fit-content(100%)_auto] gap-2">
                                <div class={"my-auto"}>Repositories</div>
                                <TextListRow entries={() => toCreate.repositories} setentries={(entries) => setToCreate("repositories", entries)}/>

                                <div class={"my-auto"}>Namespaces</div>
                                <TextListRow entries={() => toCreate.namespaces} setentries={(entries) => setToCreate("namespaces", entries)}/>

                                <div class={"my-auto"}>Lifetime</div>
                                <TextInputGroup type="number" placeholder="Expiration days" accessor={() => toCreate.lifetime?.toString() || ""} setter={(val) => {
                                    if (val === "") {
                                        setToCreate("lifetime", 0);
                                        return;
                                    }
                                    const num = parseInt(val);
                                    if (!isNaN(num)) {
                                        setToCreate("lifetime", num);
                                    }
                                }} units="days" input={{
                                    min: "1"
                                }} />
                            </div>
                            <div class="font-bold">Permissions</div>
                            <div class="grid gap-2 grid-flow-row">
                                <div class="flex flex-row gap-2.5 items-center">
                                    <input type="checkbox" checked={toCreate.canpublish} onchange={(e) => setToCreate("canpublish", e.target.checked)} />
                                    <div class="text-slate-600">Publishing</div>
                                </div>
                            </div>
                            <OrError get={status} />
                        </div>
                    </InnerElement>
                </BoxInside>
            </OuterBox></Show>
            <For each={tokensToDisplay()}>{(item) =>
                <CreatedToken token={item} delete={async () => {
                    setCreatedTokens([ ...createdTokens ].filter((it) => it.key !== item.key));
                    await api.deleteURL(`/dashboard/api/tokens/${item.key}`);
                    refetch();
                }}/>
            }</For>
        </ErrorBoundary>
    </>
}

function CreatedToken(props: { token: api.AccessToken, delete: () => Promise<void> }) {
    const [boxOpen, setBoxOpen] = createSignal(false);
    return <OuterBox>
        <RowOf>
            <button class="cursor-pointer" onclick={() => setBoxOpen(!boxOpen())}>
                <div class="flex flex-row items-center gap-5">
                    <div>{props.token.name}</div>
                    <div class="flex-1"></div>
                    <div>Expires {Temporal.Instant.from(props.token.expires).toLocaleString()}</div>
                </div>
            </button>
            <Delete ondelete={props.delete} confirmation={"simple"}>
                Are you sure you want to delete this token? Deleting a token will revoke it, permanently, and cannot be undone.
            </Delete>
        </RowOf>
        <Show when={boxOpen()}>
            <div class="py-2.5"><InnerElement>
                <div class="flex flex-col gap-2">
                    <div class="grid grid-cols-[fit-content(100%)_minmax(0,1fr)] gap-2">
                        <div class="my-auto">Key</div>
                        <TextCopyRow text={props.token.key}/>

                        <Show when={props.token.token}>
                            <div class="my-auto">Token</div>
                            <TextCopyRow text={props.token.token!}/>
                        </Show>

                        <div class={"my-auto"}>Repositories</div>
                        <TextListRow entries={() => props.token.repositories}/>

                        <div class={"my-auto"}>Namespaces</div>
                        <TextListRow entries={() => props.token.namespaces}/>
                    </div>
                    <div class="font-bold">Permissions</div>
                    <div class="grid gap-2 grid-flow-row">
                        <div class="flex flex-row gap-2.5 items-center">
                            <input type="checkbox" checked={props.token.canpublish} disabled={true} readonly={true} />
                            <div class="text-slate-600">Publishing</div>
                        </div>
                    </div>
                </div>
            </InnerElement></div>
        </Show>
    </OuterBox>
}

function NamespaceList() {
    const context = useContext(AppContext);

    const [namespaces, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/namespaces/${context!.identity.id}/list`, api.Namespaces);
    });
    const [status, setStatus] = orErrorSignal();
    const [toRequest, setToRequest] = createSignal("");
    return (
        <>
            <div class="flex flex-col gap-1">
                <TextInputGroup type="text" placeholder="Request namespace" submit="Request" onsubmit={async () => {
                    const namespaceName = toRequest();
                    const validNamespace = api.isNamespaceValid(namespaceName);
                    if (!validNamespace) {
                        setStatus({ status: "error", err: "Not a valid namespace! Must be a valid all-lowercase reversed domain name." });
                        return;
                    }
                    setStatus({ status: "working" });
                    setToRequest("");
                    mutate((namespaces) => {
                        return namespaces === undefined ? undefined : [...namespaces, { namespace: namespaceName, confirmed: false }];
                    });
                    try {
                        await api.postURL(`/dashboard/api/namespaces/${context!.identity.id}/request/${namespaceName}`)
                        setStatus({ status: "ok" });
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                    }
                    await refetch();
                }} accessor={toRequest} setter={setToRequest} />
                <div class="px-1">
                    <OrError get={status} />
                </div>
            </div>
            <Show when={namespaces()}>
            <For each={namespaces()!}>
                {(namespace) => <BoxWithHeader>
                    <RowOf>
                        <div class="flex flex-row items-center gap-5">
                            <div class={namespace.confirmed ? "font-mono" : "font-mono italic"}>{namespace.namespace}</div>
                            <div class="flex-1"></div>
                            {namespace.confirmed ? <></> : <div class="text-xs italic">(pending)</div>}
                        </div>
                    </RowOf>
                    <></>
                </BoxWithHeader>}
            </For>
            </Show>
        </>
    )
}

function AccountInfo() {
    const context = useContext(AppContext);

    return <>
        <p>{context!.identity.email} <span class="font-mono text-xs text-slate-600">({context!.identity.id})</span></p>
    </>
}

render(() => {
    const isAdminDashboardVisible =(context: AppContext) => {
        return context!.capabilities.includes("admindashboard");
    }
    return <App entries={[
        new AppInternalEntry("Account information", () => "Account information", AccountInfo),
        new AppInternalEntry("Namespaces", () => "Namespaces", NamespaceList),
        new AppInternalEntry("Tokens", () => "Tokens", TokensList),
        new AppInternalEntry("Deployments", () => "Deployments", () => <></>),
        new AppInternalEntry("Users", () => "Users", admin.AdminUsers, isAdminDashboardVisible),
        new AppInternalEntry("Repositories", () => "Repositories", admin.RepositoriesList, isAdminDashboardVisible),
        new AppInternalEntry("Backends", () => "Backends", admin.BackendsList, isAdminDashboardVisible),
        new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
        new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
    ]}/>;
}, root!);
