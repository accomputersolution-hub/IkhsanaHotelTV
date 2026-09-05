# Hostity marketing site — go.pcncloud.in

Premium public landing for the Hostity hotel & corporate TV platform.

| Host | Purpose |
|------|---------|
| `www.pcncloud.in` | Master admin (`admin-panel`) |
| `go.pcncloud.in` | This showcase |
| `{slug}.pcncloud.in` | Hotel / corporate public tenant |

Full cutover notes: [`docs/DOMAIN_PCNCLOUD.md`](../docs/DOMAIN_PCNCLOUD.md).

## Local preview

```bash
cd go-site
npx --yes serve . -p 4173
```

Open http://localhost:4173

## Deploy on Vercel

1. Vercel → **Add New Project** → this repo  
2. **Root Directory:** `go-site`  
3. Framework preset: **Other** (static)  
4. Domains → add **`go.pcncloud.in`** only  
5. DNS: `CNAME` name `go` → value Vercel shows  

Do **not** attach `www.pcncloud.in` here — that stays on the admin project.
