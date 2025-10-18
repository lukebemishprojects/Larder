/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppContext, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import { createResource, createSignal, ErrorBoundary, For, Match, Show, Switch } from 'solid-js';
import { BoxWithHeader, InnerElement, InnerHoverElements, OuterBox, TextInputGroup } from './boxes';
import { Dropdown } from './Dropdown';

const root = document.getElementById('root');

function AdminUsers(context: AppContext) {
    const [users] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/listusers', api.Users);
    })
    function NamespaceList(props: { user: string, context: AppContext }) {
        const [namespaces, { mutate, refetch }] = createResource(async () => {
            return await api.fetchJSON(`/dashboard/api/namespaces/${props.user}/list`, api.Namespaces);
        });
        function NamespaceCreation() {
            const [status, setStatus] = createSignal<{ status: "fine" } | { status: "error", err: any } | { status: "invalid" }>({ status: "fine" });
            const [toCreate, setToCreate] = createSignal("");
            return (
                <InnerElement>
                    <div class="flex flex-col gap-1">
                        <TextInputGroup type="text" placeholder="Add namespace" submit="Create" onsubmit={async () => {
                            const namespaceName = toCreate();
                            const validNamespace = api.isNamespaceValid(namespaceName);
                            if (!validNamespace) {
                                setStatus({ status: "invalid" });
                                return;
                            }
                            setStatus({ status: "fine" });
                            setToCreate("");
                            mutate((namespaces) => {
                                return namespaces === undefined ? undefined : {
                                    values: [...namespaces.values, { namespace: namespaceName, confirmed: true }]
                                };
                            });
                            try {
                                await api.postURL(`/dashboard/admin/api/namespaces/${props.user}/create/${namespaceName}`)
                            } catch (err: any) {
                                console.error(err);
                                setStatus({ status: "error", err: err });
                            }
                            await refetch();
                        }} accessor={toCreate} setter={setToCreate} />
                        <Switch>
                            <Match when={status().status == "error"}>
                                <div class="text-red-500 text-xs px-1">Error: {(status() as { err:any }).err}</div>
                            </Match>
                            <Match when={status().status == "invalid"}>
                                <div class="text-red-500 text-xs px-1">Not a valid namespace! Must be a valid all-lowercase reversed domain name.</div>
                            </Match>
                        </Switch>
                    </div>
                </InnerElement>
            )
        }
        return (
            <ErrorBoundary fallback={(error) => {
                console.log(error);
                return <InnerElement>
                    <div class="text-red-600">
                        Error: {error.message}
                    </div>
                </InnerElement>
            }}>
                <Show when={namespaces()}>
                    <InnerHoverElements basis={namespaces()!.values} foreach={(namespace) => {
                        return <ErrorBoundary fallback={(error) => {
                            console.log(error);
                            return <div class="text-red-600">
                                Error: {error.message}
                            </div>
                        }}><div class="flex flex-row gap-5 items-center text-sm">
                            <div class={namespace.confirmed ? "font-mono" : "font-mono italic"}>{namespace.namespace}</div>
                            <div class="flex-1"></div>
                            {namespace.confirmed ? <></> : <div class="text-xs italic">pending</div>}
                            <Dropdown entries={[
                                {
                                    value: "Delete",
                                    action: async () => {
                                        mutate((namespaces) => {
                                            return namespaces === undefined ? undefined : {
                                                values: namespaces.values.filter((n) => n.namespace != namespace.namespace)
                                            };
                                        });
                                        try {
                                            await api.postURL(`/dashboard/admin/api/namespaces/${props.user}/delete/${namespace.namespace}`)
                                        } catch (err: any) {
                                            console.error(err);
                                        }
                                        await refetch();
                                    }
                                }
                            ].concat(namespace.confirmed ? [] : [
                                {
                                    value: "Confirm",
                                    action: async () => {
                                        mutate((namespaces) => {
                                            return namespaces === undefined ? undefined : {
                                                values: namespaces.values.map((n) => {
                                                    if (n.namespace == namespace.namespace) {
                                                        return { namespace: n.namespace, confirmed: true };
                                                    } else {
                                                        return n;
                                                    }
                                                })
                                            };
                                        });
                                        try {
                                            await api.postURL(`/dashboard/admin/api/namespaces/${props.user}/confirm/${namespace.namespace}`)
                                        } catch (err: any) {
                                            console.error(err);
                                        }
                                        await refetch();
                                    }
                                }
                            ])}>
                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="size-5">
                                    <path d="M3 10a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM8.5 10a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM15.5 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z" />
                                </svg>
                            </Dropdown>
                        </div></ErrorBoundary>
                    }}/>
                </Show>
                <NamespaceCreation />
            </ErrorBoundary>
        )
    }
    return (
        <ErrorBoundary fallback={(error) => {
            console.log(error);
            return (<OuterBox>
                <div class="text-red-600 p-5">
                    Error: {error.message}
                </div>
            </OuterBox>)
        }}>
            <Show when={users()}>
                <For each={users()!.values}>
                    {(user) => <BoxWithHeader>
                        <div class="flex flex-row items-center gap-5">
                            <div class="">{user.email}</div>
                            <div class="flex-1"></div>
                            <div class="font-mono text-xs text-slate-600">{user.id}</div>
                        </div>
                        <>
                            <InnerElement><div class="text-slate-600 text-base">Namespaces</div></InnerElement>
                            <NamespaceList user={user.id} context={context}/>
                        </>
                    </BoxWithHeader>}
                </For>
            </Show>
        </ErrorBoundary>
    )
}

render(() => <App entries={[
    new AppInternalEntry("Users", () => "Users", AdminUsers),
    new AppInternalEntry("Repositories", () => "Repositories", () => <>
    </>),
    new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
    new AppExternalEntry("Dashboard", async () => { window.location.href = '/dashboard/' }),
    new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
]}/>, root!);
