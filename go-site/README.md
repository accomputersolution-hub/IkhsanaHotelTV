# PCN Cloud marketing site — www.pcncloud.in

Public landing for the PCN Cloud hotel & corporate TV platform.

| Host | Purpose |
|------|---------|
| **`www.pcncloud.in`** | This marketing site |
| **`pcncloud.in`** | Redirect → www |
| **`admin.pcncloud.in`** | Master admin (`admin-panel`) |
| `{slug}.pcncloud.in` | Hotel / corporate public tenant |

Full DNS split: [`docs/DOMAIN_PCNCLOUD.md`](../docs/DOMAIN_PCNCLOUD.md).

## Local preview

```bash
cd go-site
npx --yes serve . -p 4173
```

Open http://localhost:4173

## Deploy on Vercel

1. Vercel → **Add New Project** (marketing only)  
2. **Root Directory:** `go-site`  
3. Framework: **Other** (static)  
4. Domains:
   - **`www.pcncloud.in`** → Production  
   - **`pcncloud.in`** → Redirect 308 → `www.pcncloud.in`  
5. Do **not** put `admin.pcncloud.in` on this project  

On the **admin** Vercel project, keep only `admin.pcncloud.in` (+ `*.pcncloud.in` for tenants).
