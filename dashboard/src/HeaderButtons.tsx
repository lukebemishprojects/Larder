import {DELETE, Icon, SAVE} from "./icons";
import {Button, RowOf, TextInput} from "./boxes";
import {Accessor, JSX, Show, useContext} from "solid-js";
import {AppContext} from "./App";

export function Save(props: { onsave: () => Promise<void> | void, isdirty: Accessor<boolean> }) {
    return <button class="font-semibold block cursor-pointer hover:bg-slate-200 disabled:text-slate-400 disabled:bg-slate-150 disabled:cursor-auto" onclick={props.onsave} disabled={!props.isdirty()}>
        <div class="flex flex-row items-center gap-2.5">
            <div>Save</div>
            <Icon class="size-5" icon={SAVE}/>
        </div>
    </button>;
}

export function Cancel(props: { oncancel: () => Promise<void> | void, isdirty: Accessor<boolean> }) {
    return <button class="font-semibold block cursor-pointer hover:bg-slate-200 disabled:text-slate-400 disabled:bg-slate-150 disabled:cursor-auto" onclick={props.oncancel} disabled={!props.isdirty()}>
        <div class="flex flex-row items-center gap-2.5">
            <div>Cancel</div>
        </div>
    </button>;
}

export function Delete(props: { ondelete: () => Promise<void> | void, confirmation: "simple" | { "confirm": string }, children: JSX.Element }) {
    const context = useContext(AppContext)!;

    return <>
        <button class="font-semibold block cursor-pointer hover:bg-slate-200 disabled:text-slate-400 disabled:bg-slate-150 disabled:cursor-auto text-red-500" onclick={() => {
            context.modalDialogContents(() => () => {
                let confirmInput!: HTMLInputElement;

                return <>
                    <div class="rounded-md max-w-[33dvw] p-0 shadow-sm bg-slate-200 text-slate-900 flex flex-col gap-0">
                        <div class="p-2.5">
                            {props.children ?? <></>}
                        </div>
                        <div class="bg-slate-300 p-2.5 flex flex-row gap-2.5 rounded-b-md">
                            <Show when={props.confirmation !== "simple"}>
                                <RowOf class="flex-1">
                                    <TextInput input={{ ref: (e) => {
                                            confirmInput = e;
                                        } }} type="text" placeholder={`Type '${(props.confirmation as { "confirm" : string }).confirm}' to delete`} value="" onchange={() => {}} allowenter={false}/>
                                </RowOf>
                            </Show>
                            <Show when={props.confirmation === "simple"}>
                                <div class="flex-1"></div>
                            </Show>
                            <Button onclick={async () => {
                                context.modalDialog.close();
                                context.modalDialogContents(undefined);
                            }}>
                                Cancel
                            </Button>
                            <Button class="text-red-500" onclick={async () => {
                                if (props.confirmation === "simple") {
                                    await props.ondelete();
                                    context.modalDialog.close();
                                    context.modalDialogContents(undefined);
                                } else {
                                    if (confirmInput.value === props.confirmation.confirm) {
                                        await props.ondelete();
                                        context.modalDialog.close();
                                        context.modalDialogContents(undefined);
                                    } else {/* no-op */}
                                }
                            }}>
                                Delete
                            </Button>
                        </div>
                    </div>
                </>;
            });
            context.modalDialog.showModal();
            //await props.ondelete();
        }}>
            <div class="flex flex-row items-center gap-2.5">
                <div>Delete</div>
                <Icon class="size-5" icon={DELETE}/>
            </div>
        </button>
    </>;
}
