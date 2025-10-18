/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppContext, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import { createResource, createSignal, For, Match, Show, Switch } from 'solid-js';
import { BoxWithHeader, TextInputGroup } from './boxes';

const root = document.getElementById('root');

function NamespaceList(context: AppContext) {
    const [namespaces, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/namespaces/${context.identity()?.id}/list`, api.Namespaces);
    });
    const [status, setStatus] = createSignal<{ status: "fine" } | { status: "error", err: any } | { status: "invalid" }>({ status: "fine" });
    const [toRequest, setToRequest] = createSignal("");
    return (
        <>
            <Show when={namespaces()}>
            <For each={namespaces()!.values}>
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
            <div class="flex flex-col gap-1">
                <TextInputGroup type="text" placeholder="Request namespace" submit="Request" onsubmit={async () => {
                    const namespaceName = toRequest();
                    const validNamespace = api.isNamespaceValid(namespaceName);
                    if (!validNamespace) {
                        setStatus({ status: "invalid" });
                        return;
                    }
                    setStatus({ status: "fine" });
                    setToRequest("");
                    mutate((namespaces) => {
                        return namespaces === undefined ? undefined : {
                            values: [...namespaces.values, { namespace: namespaceName, confirmed: false }]
                        };
                    });
                    try {
                        await api.postURL(`/dashboard/api/namespaces/${context.identity()!.id}/request/${namespaceName}`)
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: err });
                    }
                    await refetch();
                }} accessor={toRequest} setter={setToRequest} />
                <Switch>
                    <Match when={status().status == "error"}>
                        <div class="text-red-500 text-xs px-1">Error: {(status() as { err:any }).err}</div>
                    </Match>
                    <Match when={status().status == "invalid"}>
                        <div class="text-red-500 text-xs px-1">Not a valid namespace! Must be a valid all-lowercase reversed domain name.</div>
                    </Match>
                </Switch>
            </div>
        </>
    )
}

render(() => <App entries={[
    new AppInternalEntry("Account information", () => "Account information", (context) => <>
    <p>{context.identity()!.email} <span class="font-mono text-xs text-slate-600">({context.identity()!.id})</span></p>
    </>),
    new AppInternalEntry("Namespaces", () => "Namespaces", NamespaceList),
    new AppInternalEntry("Deployments", () => "Deployments", () => <>
    </>),
    new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
    new AppExternalEntry(<div class="flex flex-row">
        <div>
        Admin Dashboard
        </div>
        <div class="flex-1"></div>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="size-5 text-slate-600">
            <path fill-rule="evenodd" d="M10 1a4.5 4.5 0 0 0-4.5 4.5V9H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-.5V5.5A4.5 4.5 0 0 0 10 1Zm3 8V5.5a3 3 0 1 0-6 0V9h6Z" clip-rule="evenodd" />
        </svg>
    </div>, async () => { window.location.href = '/dashboard/admin/' }),
    new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
]}/>, root!);
