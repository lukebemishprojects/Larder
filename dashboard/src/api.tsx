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

export const User = z.object({
    email: z.email()
});
export type User = z.infer<typeof User>;