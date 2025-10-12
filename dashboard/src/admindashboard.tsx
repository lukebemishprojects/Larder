/* @refresh reload */
import { render } from 'solid-js/web';
import 'solid-devtools';

import { App, AppExternalEntry, AppInternalEntry } from './App';

import './app.css';

const root = document.getElementById('root');

render(() => <App entries={[
    new AppInternalEntry("Users", () => "Users", () => <>
    </>),
    new AppInternalEntry("Repositories", () => "Repositories", () => <>
    </>),
    new AppExternalEntry("Browse", async () => { window.location.href = '/' }),
    new AppExternalEntry("Dashboard", async () => { window.location.href = '/dashboard/' }),
    new AppExternalEntry("Sign Out", async () => { window.location.href = '/dashboard/logout/' })
]}/>, root!);
