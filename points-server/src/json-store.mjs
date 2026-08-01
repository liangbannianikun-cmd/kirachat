import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";

export class JsonStore {
  constructor(file) {
    this.file = file;
    this.data = { version: 1, accounts: {} };
    this.writeQueue = Promise.resolve();
  }

  async load() {
    try {
      const parsed = JSON.parse(await readFile(this.file, "utf8"));
      if (parsed?.version !== 1 || typeof parsed.accounts !== "object") {
        throw new Error("Unsupported points data format");
      }
      this.data = parsed;
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
      await this.persist();
    }
  }

  update(change) {
    const operation = this.writeQueue.then(async () => {
      change(this.data);
      await this.persist();
    });
    this.writeQueue = operation.catch(() => {});
    return operation;
  }

  async persist() {
    await mkdir(path.dirname(this.file), { recursive: true });
    const temporary = `${this.file}.${process.pid}.tmp`;
    await writeFile(temporary, `${JSON.stringify(this.data, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600
    });
    await rename(temporary, this.file);
  }
}
