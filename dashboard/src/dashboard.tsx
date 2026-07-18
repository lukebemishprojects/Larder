/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppContext, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import * as admin from './admindashboard';
import { createResource, createSignal, For, Show, useContext } from 'solid-js';
import { BoxWithHeader, TextInputGroup } from './boxes';
import { OrError, orErrorSignal } from './utils';

const root = document.getElementById('root');

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
        new AppInternalEntry("Deployments", () => "Deployments", () => <>
        </>),
        new AppInternalEntry("Users", () => "Users", admin.AdminUsers, isAdminDashboardVisible),
        new AppInternalEntry("Repositories", () => "Repositories", admin.RepositoriesList, isAdminDashboardVisible),
        new AppInternalEntry("Backends", () => "Backends", admin.BackendsList, isAdminDashboardVisible),
        new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
        new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
    ]}/>;
}, root!);
