/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';

const root = document.getElementById('root');

render(() => <App entries={[
    new AppInternalEntry("Account information", () => "Account information", (context) => <>
    <p>{context.identity()!.email} <span class="font-mono text-xs text-slate-600">({context.identity()!.id})</span></p>
    </>),
    new AppInternalEntry("Namespaces", () => "Namespaces", () => <>
    </>),
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
