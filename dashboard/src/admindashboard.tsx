/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';
import * as api from './api';
import { createEffect, createResource, createSignal, ErrorBoundary, For, Setter, Show, useContext } from 'solid-js';
import { BoxInside, BoxWithHeader, Button, InnerElement, InnerHoverElements, OuterBox, TextInputGroup } from './boxes';
import { Dropdown } from './Dropdown';
import { orErrorSignal, OrError } from './utils';
import { createStore, SetStoreFunction, unwrap } from 'solid-js/store';

const root = document.getElementById('root');

function NamespaceCreation(props: { user: string, mutate: Setter<api.Namespaces | undefined>, refetch: () => Promise<unknown> | unknown }) {
    const [status, setStatus] = orErrorSignal();
    const [toCreate, setToCreate] = createSignal("");
    return (
        <InnerElement>
            <div class="flex flex-col gap-1">
                <TextInputGroup type="text" placeholder="Add namespace" submit="Create" onsubmit={async () => {
                    const namespaceName = toCreate();
                    const validNamespace = api.isNamespaceValid(namespaceName);
                    if (!validNamespace) {
                        setStatus({ status: "error", err: "Not a valid namespace! Must be a valid all-lowercase reversed domain name." });
                        return;
                    }
                    setStatus({ status: "ok" });
                    setToCreate("");
                    props.mutate((namespaces) => {
                        return namespaces === undefined ? undefined : {
                            values: [...namespaces.values, { namespace: namespaceName, confirmed: true }]
                        };
                    });
                    try {
                        await api.postURL(`/dashboard/admin/api/namespaces/${props.user}/create/${namespaceName}`)
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                    }
                    await props.refetch();
                }} accessor={toCreate} setter={setToCreate} />
                <div class="px-1">
                    <OrError get={status} />
                </div>
            </div>
        </InnerElement>
    )
}

function NamespaceList(props: { user: string }) {
    const [namespaces, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/api/namespaces/${props.user}/list`, api.Namespaces);
    });
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
            <NamespaceCreation user={props.user} mutate={mutate} refetch={refetch} />
        </ErrorBoundary>
    )
}

function AdminUsers() {
    const [users] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/listusers', api.Users);
    })
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
                            <NamespaceList user={user.id} />
                        </>
                    </BoxWithHeader>}
                </For>
            </Show>
        </ErrorBoundary>
    )
}

function RepositorySettings(props: { set: SetStoreFunction<api.Repository>, value: api.Repository }) {
    return (
        <div class="block flex flex-col gap-2">
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.supportsmavendeploy} onchange={(e) => props.set("supportsmavendeploy", e.target.checked)} />
                <div class="text-slate-600">Maven Deploy Publishing</div>
            </div>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.supportspublishportal} onchange={(e) => props.set("supportspublishportal", e.target.checked)} />
                <div class="text-slate-600">Portal Publishing</div>
            </div>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.mutable} onchange={(e) => props.set("mutable", e.target.checked)} />
                <div class="text-slate-600">Mutable Repository</div>
            </div>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.expirationdays > 0} onchange={(e) => {
                    if (e.target.checked && props.value.expirationdays == 0) {
                        props.set("expirationdays", 30);
                    } else {
                        props.set("expirationdays", 0);
                    }
                }} />
                <div class="text-slate-600">Artifacts Expire</div>
            </div>
            <Show when={props.value.expirationdays > 0}>
                <TextInputGroup type="number" placeholder="Expiration days" accessor={() => props.value.expirationdays?.toString() || ""} setter={(val) => {
                    if (val === "") {
                        return;
                    }
                    const num = parseInt(val);
                    if (!isNaN(num)) {
                        props.set("expirationdays", num);
                    }
                }} units="days" input={{
                    min: "1"
                }} />
            </Show>
        </div>
    );
}

function SingleRepository(props: { repository: api.Repository, mutate: Setter<api.Repositories | undefined>, refetch: () => Promise<unknown> | unknown }) {
    const [ toSet, setToSet ] = createStore({ ...props.repository });
    const [ isDeleting, setIsDeleting ] = createSignal(false);
    const isDirty = () => {
        let key: keyof api.Repository;
        for (key in props.repository) {
            if (toSet[key] != props.repository[key]) {
                return true;
            }
        }
        return false;
    }
    const [ status, setStatus ] = orErrorSignal();
    return <BoxWithHeader>
        <div class="flex flex-row gap-5 items-center">
            <div>{props.repository.name}</div>
            <div class="flex-1"></div>
        </div>
        <>
            <InnerElement>
                <RepositorySettings value={toSet} set={setToSet} />
            </InnerElement>
            <InnerElement>
                <div class="flex flex-row gap-2.5 w-full">
                    <Button disabled={!isDirty()} onclick={async () => {
                        setStatus({ status: "ok" });
                        const current = { ...unwrap(toSet) }
                        try {
                            await api.postJSON(`/dashboard/admin/api/repositories/${props.repository.name}`, current);
                        } catch (err: any) {
                            console.error(err);
                            setStatus({ status: "error", err: `Error: ${err}` });
                            return;
                        }
                        await props.refetch();
                    }}>
                        Save
                    </Button>
                    <Button onclick={() => {
                        if (isDeleting()) {
                            setIsDeleting(false);
                        }
                        if (isDirty()) {
                            setToSet({ ...props.repository });
                        }
                    }} disabled={!isDirty() && !isDeleting()}>
                        Cancel
                    </Button>
                    <Show when={!isDeleting()}>
                        <Button onclick={() => {
                            setIsDeleting(true);
                        }}>
                            Delete
                        </Button>
                    </Show>
                    <Show when={isDeleting()}>
                        <TextInputGroup type="text" placeholder={"Type '"+props.repository.name+"' to confirm deletion. Deletion is permanent and cannot be undone"} submit="Delete" allowenter={false} onsubmit={async (target) => {
                            if (target.value === props.repository.name) {
                                setStatus({ status: "ok" });
                                props.mutate((repositories) => {
                                    return repositories === undefined ? undefined : {
                                        values: repositories.values.filter((r) => r.name != props.repository.name)
                                    };
                                });
                                try {
                                    await api.deleteURL(`/dashboard/admin/api/repositories/${props.repository.name}`);
                                } catch (err: any) {
                                    console.error(err);
                                    setStatus({ status: "error", err: `Error: ${err}` });
                                    return;
                                }
                                await props.refetch();
                            }
                        }} />
                    </Show>
                </div>
                <OrError get={status} />
            </InnerElement>
        </>
    </BoxWithHeader>
}

function RepositoriesList() {
    const [repositories, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/repositories', api.Repositories);
    })
    const [ toCreate, setToCreate ] = createStore(api.newRepository());
    const [ status, setStatus ] = orErrorSignal();
    return (<ErrorBoundary fallback={(error) => {
        console.log(error);
        return (<OuterBox>
            <div class="text-red-600 p-5">
                Error: {error.message}
            </div>
        </OuterBox>)
    }}>
        <OuterBox>
            <TextInputGroup type="text" accessor={() => toCreate.name} setter={(value: string) => {
                setToCreate("name", value);
            }} placeholder="Create repository" submit="Create" onsubmit={async () => {
                const current = { ...unwrap(toCreate) }
                if (!api.isRepositoryNameValid(current.name)) {
                    setStatus({ status: "error", err: "Not a valid repository name! Must be lowercase alphanumeric, dots, dashes or underscores, and not a reserved path." });
                    return;
                }
                if (repositories()?.values.find((r) => r.name == current.name) !== undefined) {
                    setStatus({ status: "error", err: "A repository with that name already exists!" });
                    return;
                }
                setToCreate(api.newRepository());
                setStatus({ status: "ok" });
                mutate((repositories) => {
                    return repositories === undefined ? undefined : {
                        values: [...repositories.values, current]
                    };
                })

                try {
                    await api.postJSON(`/dashboard/admin/api/repositories/${current.name}`, current);
                } catch (err: any) {
                    console.error(err);
                    setStatus({ status: "error", err: `Error: ${err}` });
                }
                await refetch();
            }} />
            <BoxInside>
                <InnerElement>
                    <RepositorySettings value={toCreate} set={setToCreate} />
                    <OrError get={status} />
                </InnerElement>
            </BoxInside>
        </OuterBox>
        <Show when={repositories()}>
            <For each={repositories()!.values}>
                {(repository) => <SingleRepository mutate={mutate} refetch={refetch} repository={repository} />}
            </For>
        </Show>
    </ErrorBoundary>)
}

render(() => <App entries={[
    new AppInternalEntry("Users", () => "Users", AdminUsers),
    new AppInternalEntry("Repositories", () => "Repositories", RepositoriesList),
    new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
    new AppExternalEntry("Dashboard", async () => { window.location.href = '/dashboard/' }),
    new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
]}/>, root!);
