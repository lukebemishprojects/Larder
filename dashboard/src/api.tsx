import { z } from 'zod';

export async function fetchJSON<S extends z.ZodObject>(url: string, schema: S): Promise<z.infer<S>> {
    const response = await fetch(url).then((response) => {
        if (!response.ok) {
            throw new Error(`Status ${response.status}, ${response.statusText}`);
        }
        return response.json();
    });
    return schema.parse(response);
}

export async function postURL(url: string): Promise<void> {
    const response = await fetch(url, {
        method: 'POST',
    });
    if (!response.ok) {
        throw new Error(`Status ${response.status}, ${response.statusText}`);
    }
}

export async function deleteURL(url: string): Promise<void> {
    const response = await fetch(url, {
        method: 'DELETE',
    });
    if (!response.ok) {
        throw new Error(`Status ${response.status}, ${response.statusText}`);
    }
}

export const User = z.object({
    email: z.email(),
    id: z.uuid()
});
export type User = z.infer<typeof User>;

export const ListResponse = function <T>(itemSchema: z.ZodType<T>) {
    return z.object({
        values: z.array(itemSchema)
    });
};
export type ListResponse<T> = {
    values: T[];
};

export const Users = ListResponse(User);
export type Users = ListResponse<User>;

export const Namespace = z.object({
    namespace: z.string()
});
export type Namespace = z.infer<typeof Namespace>;

export const Namespaces = ListResponse(Namespace);
export type Namespaces = ListResponse<Namespace>;