type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare function execLua(args: Array<string>): number;
export declare function execLuac(args: Array<string>): number;
export as namespace tenum_cli;