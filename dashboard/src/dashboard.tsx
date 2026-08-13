/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppContext, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import * as admin from './admindashboard';
import {createMemo, createResource, createSignal, ErrorBoundary, For, Show, useContext} from 'solid-js';
import {BoxWithHeader, InnerElement, OuterBox, TextCopy, TextInputGroup} from './boxes';
import { OrError, orErrorSignal } from './utils';
import { z } from 'zod';
import {createStore} from "solid-js/store";

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
        const names = createdTokens.map((i) => i.name);
        const tokensOut: api.AccessToken[] = [];
        for (const token of createdTokens) {
            tokensOut.push(token);
        }
        if (tokens()) {
            for (const token of (tokens()!)) {
                if (!(token.name in names)) {
                    tokensOut.push(token);
                }
            }
        }
        return tokensOut;
    });

    return <>
        <ErrorBoundary fallback={(error) => {
            console.error(error);
            return (<OuterBox>
                <div class="text-red-600 p-5">
                    Error: {error.message}
                </div>
            </OuterBox>)
        }}>
            <CreatedToken token={{
                name: "Test A",
                key: "key",
                token: "token",
                canpublish: true,
                repositories: ["releases"],
                namespaces: ["org.example"],
                expires: new Date(Date.now())
            }}/>
            <CreatedToken token={{
                name: "Test B",
                key: "key",
                canpublish: false,
                repositories: ["snapshots"],
                namespaces: ["org.example.inner"],
                expires: new Date(Date.now())
            }}/>
        </ErrorBoundary>
    </>
}

function CreatedToken(props: { token: api.AccessToken }) {
    return <>
        <BoxWithHeader>
            <div class="flex flex-row items-center gap-5">
                <div>{props.token.name}</div>
                <div class="flex-1"></div>
                <div>Expires {props.token.expires.toLocaleString()}</div>
            </div>
            <InnerElement>
                <div class="flex flex-col gap-2">
                    <div class="grid grid-cols-[fit-content(100%)_auto] gap-2">
                        <div class="my-auto">Key</div>
                        <TextCopy text={props.token.key}/>

                        <Show when={props.token.token}>
                            <div class="my-auto">Token</div>
                            <TextCopy text={props.token.token!}/>
                        </Show>
                    </div>
                </div>
            </InnerElement>
        </BoxWithHeader>
    </>
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
                    <div class="flex flex-row gap-5 items-center">
                        <div class={namespace.confirmed ? "font-mono" : "font-mono italic"}>{namespace.namespace}</div>
                        <div class="flex-1"></div>
                        {namespace.confirmed ? <></> : <div class="text-xs italic">(pending)</div>}
                    </div>
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
