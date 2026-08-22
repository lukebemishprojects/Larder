import 'solid-devtools';

import './app.css';
import * as api from './api';
import { createContext, createEffect, createResource, createMemo, createSignal, ErrorBoundary, For, Setter, Show, useContext } from 'solid-js';
import {
    BoxInside, BoxWithHeader, Button, InnerElement, InnerHoverElements, OuterBox, TextInputRow, TextInputGroup,
    RowOf, BoxWithPartialHeader
} from './boxes';
import { Dropdown } from './Dropdown';
import { z } from 'zod';
import { orErrorSignal, OrError } from './utils';
import { createStore, SetStoreFunction, unwrap } from 'solid-js/store';
import {DOTDOTDOT, DROPDOWN, DELETE, Icon} from "./icons";
import {Cancel, Delete, Save} from "./HeaderButtons";

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
                            <Icon icon={DOTDOTDOT} class="size-5"/>
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
                        <RowOf>
                            <div class="flex flex-row items-center gap-5">
                                <div class="">{user.email}</div>
                                <div class="flex-1"></div>
                                <div class="font-mono text-xs text-slate-600">{user.id}</div>
                            </div>
                        </RowOf>
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

function RepositorySettings(props: { set: SetStoreFunction<api.Repository>, value: api.Repository }) {
    const context = useContext(BackendsAvailable)!;

    const backendType = createMemo<api.BackendConfiguration["type"] | undefined>(() => props.value.backend ? context.backends.find((b) => b.id == props.value.backend)?.type : undefined);
    const deploymentBackendType = createMemo<api.BackendConfiguration["type"] | undefined>(() => props.value.deploymentbackend ? context.backends.find((b) => b.id == props.value.deploymentbackend)?.type : undefined);

    return (
        <div class="flex flex-col gap-2">
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.supportsmavendeploy} onchange={(e) => props.set("supportsmavendeploy", e.target.checked)} />
                <div class="text-slate-600">Maven Deploy Publishing</div>
            </div>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.supportspublishportal} onchange={(e) => {
                    props.set("supportspublishportal", e.target.checked);
                    props.set("deploymentbackend", undefined);
                    props.set("deployments3backend", undefined);
                    props.set("deploymentfilesystembackend", undefined);
                }} />
                <div class="text-slate-600">Portal Publishing</div>
            </div>
            <Show when={props.value.supportspublishportal}>
                <div class="flex flex-col gap-2 pl-2.5">
                    <RowOf><Dropdown dropdownWidth='w-96' classes="p-2.5 font-semibold text-sm rounded-md hover:bg-slate-150" entries={context.backends.map((backend) => {
                        return {
                            value: <div class="flex flex-row gap-2.5 w-full items-center">
                                <div>{api.backendTypePrettyName(backend.type)}</div>
                                <div class="flex-1"></div>
                                <div class="font-mono text-xs text-slate-600">{backend.id}</div>
                            </div>,
                            action: async () => {
                                if (props.value.deploymentbackend != backend.id) {
                                    props.set("deployments3backend", undefined);
                                    props.set("deploymentfilesystembackend", undefined);
                                    if (backend.type == "s3backend") {
                                        props.set("deployments3backend", api.newS3BackendConfiguration())
                                    } else if (backend.type == "filesystembackend") {
                                        props.set("deploymentfilesystembackend", api.newFilesystemBackendConfiguration())
                                    }
                                }
                                props.set("deploymentbackend", backend.id);
                            }
                        }
                    })}>
                        {props.value.deploymentbackend ? (() => {
                            const matching = context.backends.find((b) => b.id == props.value.deploymentbackend)!
                            return <div class="flex flex-row gap-2.5 w-full items-center">
                                <div>{api.backendTypePrettyName(matching.type)}</div>
                                <div class="flex-1"></div>
                                <div class="font-mono text-xs text-slate-600">{matching.id}</div>
                            </div>
                        })() : "Select deployment staging backend"}
                        <Icon class="-mr-1 size-5 text-slate-600" icon={DROPDOWN}/>
                        <div class="flex-1"></div>
                    </Dropdown></RowOf>
                    <Show when={deploymentBackendType() == "s3backend"}>
                        <TextInputRow type="text" placeholder="S3 bucket" value={props.value.deployments3backend!.bucket} onchange={(v) => {props.set("deployments3backend", "bucket", v)}} />
                        <TextInputRow type="text" placeholder="Prefix in bucket" value={props.value.deployments3backend!.prefix} onchange={(v) => {props.set("deployments3backend", "prefix", v)}} />
                    </Show>
                    <Show when={deploymentBackendType() == "filesystembackend"}>
                        <TextInputRow type="text" placeholder="Prefix in filesystem" value={props.value.deploymentfilesystembackend!.prefix} onchange={(v) => {props.set("deploymentfilesystembackend", "prefix", v)}} />
                    </Show>
                </div>
            </Show>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.mutable} onchange={(e) => props.set("mutable", e.target.checked)} />
                <div class="text-slate-600">Mutable Repository</div>
            </div>
            <div class="flex flex-row gap-2.5 items-center">
                <input type="checkbox" checked={props.value.supportssnapshots} onchange={(e) => props.set("supportssnapshots", e.target.checked)} />
                <div class="text-slate-600">Supports Snapshots</div>
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
                <div class="flex flex-col gap-2 pl-2.5">
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
                </div>
            </Show>
            <RowOf><Dropdown dropdownWidth='w-96' classes="p-2.5 font-semibold text-sm rounded-md hover:bg-slate-150" entries={context.backends.map((backend) => {
                return {
                    value: <div class="flex flex-row gap-2.5 w-full items-center">
                        <div>{api.backendTypePrettyName(backend.type)}</div>
                        <div class="flex-1"></div>
                        <div class="font-mono text-xs text-slate-600">{backend.id}</div>
                    </div>,
                    action: async () => {
                        if (props.value["backend"] != backend.id) {
                            props.set("s3backend", undefined);
                            props.set("filesystembackend", undefined);
                            if (backend.type == "s3backend") {
                                props.set("s3backend", api.newS3BackendConfiguration())
                            } else if (backend.type == "filesystembackend") {
                                props.set("filesystembackend", api.newFilesystemBackendConfiguration())
                            }
                        }
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
                <Icon class="-mr-1 size-5 text-slate-600" icon={DROPDOWN}/>
                <div class="flex-1"></div>
            </Dropdown></RowOf>
            <Show when={backendType() == "s3backend"}>
                <TextInputRow type="text" placeholder="S3 bucket" value={props.value.s3backend!.bucket} onchange={(v) => {props.set("s3backend", "bucket", v)}} />
                <TextInputRow type="text" placeholder="Prefix in bucket" value={props.value.s3backend!.prefix} onchange={(v) => {props.set("s3backend", "prefix", v)}} />
            </Show>
            <Show when={backendType() == "filesystembackend"}>
                <TextInputRow type="text" placeholder="Prefix in filesystem" value={props.value.filesystembackend!.prefix} onchange={(v) => {props.set("filesystembackend", "prefix", v)}} />
            </Show>
        </div>
    );
}

function SingleRepository(props: { repository: api.Repository, mutate: Setter<api.Repositories | undefined>, refetch: () => Promise<unknown> | unknown }) {
    const context = useContext(BackendsAvailable)!;
    const [ toSet, setToSet ] = createStore(structuredClone({
        ...props.repository
    }));
    const isDirty = createMemo(() => {
        return !api.zodEquals(api.Repository, toSet, props.repository);
    });
    const [ status, setStatus ] = orErrorSignal();
    return <BoxWithPartialHeader>
        {(toggle) => <RowOf>
            <button class="flex" onclick={toggle}>{props.repository.name}<div class="flex-1"></div></button>
            <Cancel oncancel={() => {
                if (isDirty()) {
                    setToSet({
                        ...props.repository
                    });
                }
            }} isdirty={isDirty}/>
            <Save onsave={async () => {
                const current: api.Repository = { ...unwrap(toSet) }
                if (!api.validateRepository(current, setStatus)) {
                    return;
                }
                setStatus({ status: "working" });
                try {
                    await api.postJSON(`/dashboard/admin/api/repositories/${props.repository.name}`, current, api.Repository);
                    setStatus({ status: "ok" });
                } catch (err: any) {
                    console.error(err);
                    setStatus({ status: "error", err: `Error: ${err}` });
                    return;
                }
                await props.refetch();
            }} isdirty={isDirty}/>
            <Delete ondelete={async () => {
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
            }} confirmation={{ confirm: props.repository.name }}>
                Are you sure you want to delete this repository? Deletion is permanent and cannot be undone.
            </Delete>
        </RowOf>}
        <>
            <InnerElement>
                <RepositorySettings value={toSet} set={setToSet} />
                <OrError get={status} />
            </InnerElement>
        </>
    </BoxWithPartialHeader>
}

export function RepositoriesList() {
    const [repositories, { mutate, refetch }] = createResource(async () => {
        return await api.fetchJSON('/dashboard/admin/api/repositories', api.Repositories);
    })
    const [ toCreate, setToCreate ] = createStore(api.newRepository());
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
                    const current: api.Repository = { ...unwrap(toCreate) }

                    if (!api.validateRepository(current, setStatus)) {
                        return;
                    }

                    if (repositories()?.find((r) => r.name == current.name) !== undefined) {
                        setStatus({ status: "error", err: "A repository with that name already exists!" });
                        return;
                    }
                    setToCreate(api.newRepository());
                    setStatus({ status: "working" });
                    mutate((repositories) => {
                        return repositories === undefined ? undefined : [...repositories, current];
                    })

                    try {
                        await api.postJSON(`/dashboard/admin/api/repositories/${current.name}`, current, api.Repository);
                        setStatus({ status: "ok" });
                    } catch (err: any) {
                        console.error(err);
                        setStatus({ status: "error", err: `Error: ${err}` });
                    }
                    await refetch();
                }} /></div>
                <BoxInside>
                    <InnerElement>
                        <RepositorySettings value={toCreate} set={setToCreate} />
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

function BackendContents(props: { toSet: api.BackendConfiguration, setToSet: SetStoreFunction<api.BackendConfiguration> }) {
    const [filesystemLocations] = createResource(async () => {
        return await api.fetchJSON(`/dashboard/admin/api/backends/filesystem`, z.array(z.string()));
    });

    return (
        <div class="flex flex-col gap-2">
            <Show when={props.toSet.type == "s3backend"}>
                <div class="flex flex-col gap-2">
                    <TextInputRow type="text" placeholder="Region" value={props.toSet.s3backend!.region} onchange={(v) => {props.setToSet("s3backend", "region", v)}} />
                    <TextInputRow type="text" placeholder="Endpoint" value={props.toSet.s3backend!.endpoint} onchange={(v) => {props.setToSet("s3backend", "endpoint", v)}} />
                    <TextInputRow type="text" placeholder="Access Key ID" value={props.toSet.s3backend!.accesskeyid} onchange={(v) => {props.setToSet("s3backend", "accesskeyid", v)}} />
                    <TextInputRow type="text" placeholder="Secret Access Key" value={props.toSet.s3backend!.secretaccesskey ?? ""} onchange={(v) => {
                        if (v.length > 0) {
                            props.setToSet("s3backend", "secretaccesskey", v);
                        } else {
                            props.setToSet("s3backend", "secretaccesskey", undefined);
                        }
                    }} />
                </div>
            </Show>
            <Show when={props.toSet.type == "filesystembackend" && filesystemLocations()}>
                <div class="flex flex-col gap-2">
                    <RowOf><Dropdown dropdownWidth='w-96' classes="p-2.5 font-semibold text-sm rounded-md hover:bg-slate-150" entries={filesystemLocations()!.map((location) => {
                        return {
                            value: <div class="flex flex-row gap-2.5 w-full items-center">
                                <div>{location}</div>
                                <div class="flex-1"></div>
                            </div>,
                            action: async () => {
                                props.setToSet("filesystembackend", "location", location)
                            }
                        }
                    })}>
                        {props.toSet.filesystembackend!.location ? <div class="flex flex-row gap-2.5 w-full items-center">
                            <div>{props.toSet.filesystembackend!.location}</div>
                            <div class="flex-1"></div>
                        </div> : "Select location"}
                        <Icon class="-mr-1 size-5 text-slate-600" icon={DROPDOWN}/>
                        <div class="flex-1"></div>
                    </Dropdown></RowOf>
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
                const [ toSet, setToSet ] = createStore<api.BackendConfiguration>(structuredClone({
                    ...unwrap(item())
                }));
                const isDirty = createMemo(() => {
                    return !api.zodEquals(api.BackendConfiguration, toSet, item());
                });
                return <BoxWithHeader>
                    <RowOf>
                        <div class="flex flex-row items-center gap-2.5">
                            <div>{api.backendTypePrettyName(item().type)}</div>
                            <div class="flex-1"></div>
                            <div class="font-mono text-xs text-slate-600">{props.backend.id}</div>
                        </div>
                        <Cancel oncancel={() => {
                            if (isDirty()) {
                                setToSet(structuredClone({
                                    ...unwrap(item())
                                }));
                            }
                        }} isdirty={isDirty}/>
                        <Save onsave={async () => {
                            setStatus({ status: "working" });
                            const current = { ...unwrap(toSet) }
                            try {
                                await api.postJSON(`/dashboard/admin/api/backends/${props.backend.id}`, current, api.BackendConfiguration);
                                setStatus({ status: "ok" });
                            } catch (err: any) {
                                console.error(err);
                                setStatus({ status: "error", err: `Error: ${err}` });
                                return;
                            }
                            await refetch();
                        }} isdirty={isDirty}/>
                        <Delete ondelete={async () => {
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
                        }} confirmation={"simple"}>
                            Are you sure you want to delete this backend? Deletion is permanent and cannot be undone.
                        </Delete>
                    </RowOf>
                    <>
                        <InnerElement>
                            <BackendContents toSet={toSet} setToSet={setToSet}/>
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
            <RowOf>
                <div class="flex-1 text-sm">Create backend</div>
                <Dropdown classes="p-2.5 font-semibold text-sm hover:bg-slate-150" entries={possibleTypes.map((type) => {
                    return {
                        value: api.backendTypePrettyName(type),
                        action: async () => {
                            if (type === "s3backend") {
                                if (toCreate.s3backend === undefined) {
                                    setToCreate("s3backend", api.newS3Backend());
                                }
                            }

                            if (type === "filesystembackend") {
                                if (toCreate.filesystembackend === undefined) {
                                    setToCreate("filesystembackend", api.newFilesystemBackend());
                                }
                            }

                            setToCreate("type", type);

                            if (type !== "s3backend") {
                                setToCreate("s3backend", undefined);
                            }

                            if (type !== "filesystembackend") {
                                setToCreate("filesystembackend", undefined);
                            }
                        }
                    }
                })}>
                    {api.backendTypePrettyName(toCreate.type)}
                    <Icon class="-mr-1 size-5 text-slate-600" icon={DROPDOWN}/>
                </Dropdown>
                <button class="font-semibold text-sm cursor-pointer hover:bg-slate-200" onclick={async () => {
                    const current = { ...unwrap(toCreate) }
                    setToCreate(api.newBackendConfiguration());
                    setStatus({ status: "working" });
                    try {
                        await api.postJSON(`/dashboard/admin/api/backends`, current, api.BackendConfiguration);
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
            </RowOf>
            <BoxInside>
                <InnerElement>
                    <BackendContents toSet={toCreate} setToSet={setToCreate} />
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
