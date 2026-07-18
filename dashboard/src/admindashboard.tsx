import 'solid-devtools';

import './app.css';
import * as api from './api';
import { createContext, createEffect, createResource, createSignal, ErrorBoundary, For, Setter, Show, Accessor, useContext } from 'solid-js';
import { BoxInside, BoxWithHeader, Button, InnerElement, InnerHoverElements, OuterBox, TextInput, TextInputGroup } from './boxes';
import { Dropdown } from './Dropdown';
import { orErrorSignal, OrError } from './utils';
import { createStore, SetStoreFunction, unwrap } from 'solid-js/store';

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
                    setStatus({ status: "working" });
                    setToCreate("");
                    props.mutate((namespaces) => {
                        return namespaces === undefined ? undefined : [...namespaces, { namespace: namespaceName, confirmed: true }];
                    });
                    try {
                        await api.postURL(`/dashboard/admin/api/namespaces/${props.user}/create/${namespaceName}`)
                        setStatus({ status: "ok" });
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
            console.error(error);
            return <InnerElement>
                <div class="text-red-600">
                    Error: {error.message}
                </div>
            </InnerElement>
        }}>
            <Show when={namespaces()}>
                <InnerHoverElements basis={namespaces()!} foreach={(namespace) => {
                    return <ErrorBoundary fallback={(error) => {
                        console.error(error);
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
                                        return namespaces === undefined ? undefined : namespaces.filter((n) => n.namespace != namespace.namespace);
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
                                        return namespaces === undefined ? undefined : namespaces.map((n) => {
                                                if (n.namespace == namespace.namespace) {
                                                    return { namespace: n.namespace, confirmed: true };
                                                } else {
                                                    return n;
                                                }
                                            });
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

export function AdminUsers() {
    const [users] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/users', api.Users);
    })
    return (
        <ErrorBoundary fallback={(error) => {
            console.error(error);
            return (<OuterBox>
                <div class="text-red-600 p-5">
                    Error: {error.message}
                </div>
            </OuterBox>)
        }}>
            <Show when={users()}>
                <For each={users()!}>
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

interface BackendsAvailable {
    backends: api.Backends
}
const BackendsAvailable = createContext<BackendsAvailable>();

function RepositorySettings(props: { set: SetStoreFunction<api.Repository>, value: api.Repository, s3set: SetStoreFunction<api.S3BackendConfiguration>, s3value: api.S3BackendConfiguration, backendType: Accessor<api.BackendConfiguration["type"] | undefined>, setBackendType: Setter<api.BackendConfiguration["type"] | undefined> }) {
    const context = useContext(BackendsAvailable)!;

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
            <Dropdown dropdownWidth='w-96' classes="py-2.5 px-3 rounded-md bg-white font-semibold text-sm border-1 hover:bg-slate-150" entries={context.backends.map((backend) => {
                return {
                    value: <div class="flex flex-row gap-2.5 w-full items-center">
                        <div>{api.backendTypePrettyName(backend.type)}</div>
                        <div class="flex-1"></div>
                        <div class="font-mono text-xs text-slate-600">{backend.id}</div>
                    </div>,
                    action: async () => {
                        props.setBackendType(backend.type);
                        props.set("backend", backend.id);
                    }
                }
            })}>
                {props.value.backend ? (() => {
                    const matching = context.backends.find((b) => b.id == props.value.backend)!
                    return <div class="flex flex-row gap-2.5 w-full items-center">
                        <div>{api.backendTypePrettyName(matching.type)}</div>
                        <div class="flex-1"></div>
                        <div class="font-mono text-xs text-slate-600">{matching.id}</div>
                    </div>
                })() : "Select backend"}
                <svg class="-mr-1 size-5 text-slate-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" data-slot="icon">
                    <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
                </svg>
            </Dropdown>
            <Show when={props.backendType() == "s3backend"}>
                <TextInput type="text" placeholder="S3 bucket" value={props.s3value.bucket} onchange={(v) => {props.s3set("bucket", v)}} />
                <TextInput type="text" placeholder="Prefix in bucket" value={props.s3value.prefix} onchange={(v) => {props.s3set("prefix", v)}} />
            </Show>
        </div>
    );
}

function SingleRepository(props: { repository: api.Repository, mutate: Setter<api.Repositories | undefined>, refetch: () => Promise<unknown> | unknown }) {
    const context = useContext(BackendsAvailable)!;

    const [ toSet, setToSet ] = createStore({
        ...props.repository,
        s3backend: undefined
    });
    const [ backendType, setBackendType ] = createSignal<api.BackendConfiguration["type"] | undefined>(context.backends.find((b) => b.id == props.repository.backend)?.type);
    const [ toSetS3, setToSetS3 ] = createStore(props.repository.s3backend ? { ...props.repository.s3backend! } : api.newS3BackendConfiguration());
    const [ isDeleting, setIsDeleting ] = createSignal(false);
    const isDirty = () => {
        let key: keyof api.Repository;
        for (key in props.repository) {
            if (key == "s3backend") {
                continue;
            }
            if (toSet[key] != props.repository[key]) {
                return true;
            }
        }
        if (backendType() == "s3backend") {
            let s3key: keyof api.S3BackendConfiguration;
            for (s3key in props.repository.s3backend!) {
                if (toSetS3[s3key] != props.repository.s3backend![s3key]) {
                    return true;
                }
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
                <RepositorySettings value={toSet} set={setToSet} s3value={toSetS3} s3set={setToSetS3} backendType={backendType} setBackendType={setBackendType} />
            </InnerElement>
            <InnerElement>
                <div class="flex flex-row gap-2.5 w-full items-center">
                    <Button disabled={!isDirty()} onclick={async () => {
                        const current = { ...unwrap(toSet), s3backend: undefined }
                        if (backendType() === "s3backend") {
                            current.s3backend = { ...unwrap(toSetS3) }
                        }
                        if (!api.validateRepository(current, setStatus)) {
                            return;
                        }
                        setStatus({ status: "working" });
                        try {
                            await api.postJSON(`/dashboard/admin/api/repositories/${props.repository.name}`, current);
                            setStatus({ status: "ok" });
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
                            setToSet({
                                ...props.repository,
                                s3backend: undefined
                            });
                            setToSetS3(props.repository.s3backend ? {
                                ...props.repository.s3backend!
                            } : api.newS3BackendConfiguration());
                            setBackendType(context.backends.find((b) => b.id == props.repository.backend)?.type);
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
                                setStatus({ status: "working" });
                                try {
                                    await api.deleteURL(`/dashboard/admin/api/repositories/${props.repository.name}`);
                                    setStatus({ status: "ok" });
                                    await props.refetch();
                                } catch (err: any) {
                                    console.error(err);
                                    setStatus({ status: "error", err: `${err}` });
                                    return;
                                }
                            }
                        }} />
                    </Show>
                </div>
                <OrError get={status} />
            </InnerElement>
        </>
    </BoxWithHeader>
}

export function RepositoriesList() {
    const [repositories, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/repositories', api.Repositories);
    })
    const [ toCreate, setToCreate ] = createStore(api.newRepository());
    const [ backendType, setBackendType ] = createSignal<api.BackendConfiguration["type"] | undefined>(undefined);
    const [ toCreateS3, setToCreateS3 ] = createStore(api.newS3BackendConfiguration());
    const [ status, setStatus ] = orErrorSignal();
    const [ backends ] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/backends', api.Backends);
    })
    return (<ErrorBoundary fallback={(error) => {
        console.error(error);
        return (<OuterBox>
            <div class="text-red-600 p-5">
                Error: {error.message}
            </div>
        </OuterBox>)
    }}>
        <Show when={backends()}><BackendsAvailable.Provider value={{ backends: backends()! }}>
            <OuterBox>
                <div class="shadow-sm"><TextInputGroup type="text" accessor={() => toCreate.name} setter={(value: string) => {
                    setToCreate("name", value);
                }} placeholder="Create repository" submit="Create" onsubmit={async () => {
                    const current: api.Repository = { ...unwrap(toCreate), s3backend: undefined }
                    if (backendType() == "s3backend") {
                        current.s3backend = { ...unwrap(toCreateS3) };
                    }

                    if (!api.validateRepository(current, setStatus)) {
                        return;
                    }

                    if (repositories()?.find((r) => r.name == current.name) !== undefined) {
                        setStatus({ status: "error", err: "A repository with that name already exists!" });
                        return;
                    }
                    setToCreate(api.newRepository());
                    setToCreateS3(api.newS3BackendConfiguration());
                    setStatus({ status: "working" });
                    mutate((repositories) => {
                        return repositories === undefined ? undefined : [...repositories, current];
                    })

                    try {
                        await api.postJSON(`/dashboard/admin/api/repositories/${current.name}`, current);
                        setStatus({ status: "ok" });
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                    }
                    await refetch();
                }} /></div>
                <BoxInside>
                    <InnerElement>
                        <RepositorySettings value={toCreate} set={setToCreate} s3value={toCreateS3} s3set={setToCreateS3} backendType={backendType} setBackendType={setBackendType} />
                        <OrError get={status} />
                    </InnerElement>
                </BoxInside>
            </OuterBox>
            <Show when={repositories()}>
                <For each={repositories()!}>
                    {(repository) => <SingleRepository mutate={mutate} refetch={refetch} repository={repository} />}
                </For>
            </Show>
        </BackendsAvailable.Provider></Show>
    </ErrorBoundary>)
}

function BackendContents(props: { toSet: api.BackendConfiguration, setToSet: SetStoreFunction<api.BackendConfiguration>, toSetS3: api.S3Backend, setToSetS3: SetStoreFunction<api.S3Backend> }) {
    createEffect(() => {
        if (props.toSet.type == "s3backend") {
            if (props.toSet.s3backend === undefined) {
                props.setToSet("s3backend", api.newS3Backend());
            }
        } else {
            props.setToSet("s3backend", undefined);
        }
    });

    return (
        <div class="block flex flex-col gap-2">
            <Show when={props.toSet.type == "s3backend"}>
                <div class="block flex flex-col gap-2">
                    <TextInput type="text" placeholder="Region" value={props.toSetS3.region} onchange={(v) => {props.setToSetS3("region", v)}} />
                    <TextInput type="text" placeholder="Endpoint" value={props.toSetS3.endpoint} onchange={(v) => {props.setToSetS3("endpoint", v)}} />
                    <TextInput type="text" placeholder="Access Key ID" value={props.toSetS3.accesskeyid} onchange={(v) => {props.setToSetS3("accesskeyid", v)}} />
                    <TextInput type="text" placeholder="Secret Access Key" value={props.toSetS3.secretaccesskey ?? ""} onchange={(v) => {
                        if (v.length > 0) {
                            props.setToSetS3("secretaccesskey", v);
                        } else {
                            props.setToSetS3("secretaccesskey", undefined);
                        }
                    }} />
                </div>
            </Show>
        </div>
    )
}

function SingleBackend(props: { backend: api.Backend, mutate: Setter<api.Backends | undefined>, refetch: () => Promise<unknown> | unknown }) {
    const [ backendConfiguration, { refetch } ] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/admin/api/backends/${props.backend.id}`, api.BackendConfiguration);
    });
    const [ status, setStatus ] = orErrorSignal();
    const [ isDeleting, setIsDeleting ] = createSignal(false);
    return <ErrorBoundary fallback={(error) => {
        console.error(error);
        return (<OuterBox>
            <div class="text-red-600 p-5">
                Error: {error.message}
            </div>
        </OuterBox>)
    }}>
        <Show when={backendConfiguration()}>
            {(item) => {
                const [ toSet, setToSet ] = createStore<api.BackendConfiguration>({
                    s3backend: undefined,
                    ...item()
                });
                const [ toSetS3, setToSetS3 ] = createStore<api.S3Backend>(item().s3backend ? { ...item().s3backend! } : api.newS3Backend());
                const isDirty = () => {
                    if (item().type != toSet.type) {
                        return true;
                    }
                    const type = toSet.type;
                    if (item().type != type) {
                        return true;
                    }
                    if (type == "s3backend") {
                        let key: keyof api.S3Backend;
                        for (key in item().s3backend) {
                            if (toSetS3[key] != item().s3backend![key]) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                return <BoxWithHeader>
                    <div class="flex flex-row gap-2.5 w-full items-center">
                        <div>{api.backendTypePrettyName(item().type)}</div>
                        <div class="flex-1"></div>
                        <div class="font-mono text-xs text-slate-600">{props.backend.id}</div>
                    </div>
                    <>
                        <InnerElement>
                            <BackendContents toSet={toSet} setToSet={setToSet} toSetS3={toSetS3} setToSetS3={setToSetS3} />
                        </InnerElement>
                        <InnerElement>
                            <div class="flex flex-row gap-2.5 w-full items-center">
                                <Button disabled={!isDirty()} onclick={async () => {
                                    setStatus({ status: "working" });
                                    const current = { ...unwrap(toSet) }
                                    if (current.type == "s3backend") {
                                        current.s3backend = { ...unwrap(toSetS3) };
                                        setToSetS3("secretaccesskey", undefined);
                                    }
                                    try {
                                        await api.postJSON(`/dashboard/admin/api/backends/${props.backend.id}`, current);
                                        setStatus({ status: "ok" });
                                    } catch (err: any) {
                                        console.error(err);
                                        setStatus({ status: "error", err: `Error: ${err}` });
                                        return;
                                    }
                                    await refetch();
                                }}>
                                    Save
                                </Button>
                                <Button onclick={() => {
                                    if (isDeleting()) {
                                        setIsDeleting(false);
                                    }
                                    if (isDirty()) {
                                        setToSet({ ...item() });
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
                                    <Button onclick={async () => {
                                        setStatus({ status: "working" });
                                        try {
                                            await api.deleteURL(`/dashboard/admin/api/backends/${props.backend.id}`);
                                            setStatus({ status: "ok" });
                                            await props.refetch();
                                        } catch (err: any) {
                                            console.error(err);
                                            setStatus({ status: "error", err: `Error: ${err}` });
                                            return;
                                        }
                                    }}>
                                        Confirm
                                    </Button>
                                    <div class="text-red-500">
                                        Deletion is permanent and cannot be undone. Please confirm.
                                    </div>
                                </Show>
                            </div>
                            <OrError get={status} />
                        </InnerElement>
                    </>
                </BoxWithHeader>
            }}
        </Show>
    </ErrorBoundary>;
}

export function BackendsList() {
    const [ backends, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/backends', api.Backends);
    })
    const [ toCreate, setToCreate ] = createStore(api.newBackendConfiguration());
    const [ toCreateS3, setToCreateS3 ] = createStore<api.S3Backend>(api.newS3Backend());
    const [ status, setStatus ] = orErrorSignal();

    const possibleTypes: api.BackendConfiguration["type"][] = api.BackendConfiguration.shape.type.options;

    return (<ErrorBoundary fallback={(error) => {
        console.error(error);
        return (<OuterBox>
            <div class="text-red-600 p-5">
                Error: {error.message}
            </div>
        </OuterBox>)
    }}>
        <OuterBox>
            <div class="bg-white shadow-sm rounded-md flex flex-row w-full">
                <div class="text-sm py-2.5 px-3 flex-1">Create backend</div>
                <Dropdown classes="py-2.5 px-3 rounded-md bg-white font-semibold text-sm border-1 hover:bg-slate-150 rounded-r-none" entries={possibleTypes.map((type) => {
                    return {
                        value: api.backendTypePrettyName(type),
                        action: async () => setToCreate("type", type)
                    }
                })}>
                    {api.backendTypePrettyName(toCreate.type)}
                    <svg class="-mr-1 size-5 text-slate-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" data-slot="icon">
                        <path fill-rule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
                    </svg>
                </Dropdown>
                <button class="border-l-0 font-semibold bg-white rounded-md text-sm border-1 py-2.5 px-3 block cursor-pointer bg-slate-150 hover:bg-slate-200 rounded-l-none" onclick={async () => {
                    const current = { ...unwrap(toCreate) }
                    if (current.type == "s3backend") {
                        current.s3backend = { ...unwrap(toCreateS3) };
                    }
                    setToCreate(api.newBackendConfiguration());
                    setToCreateS3(api.newS3Backend());
                    setStatus({ status: "working" });
                    try {
                        await api.postJSON(`/dashboard/admin/api/backends`, current);
                        setStatus({ status: "ok" });
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                        return;
                    }

                    await refetch();
                }}>
                    Create
                </button>
            </div>
            <BoxInside>
                <InnerElement>
                    <BackendContents toSet={toCreate} setToSet={setToCreate} toSetS3={toCreateS3} setToSetS3={setToCreateS3} />
                    <OrError get={status} />
                </InnerElement>
            </BoxInside>
        </OuterBox>
        <Show when={backends()}>
            <For each={backends()!}>
                {(backend) => <SingleBackend mutate={mutate} refetch={refetch} backend={backend} />}
            </For>
        </Show>
    </ErrorBoundary>)
}
