# Hostity marketing site — go.pcncloud.in

Premium public landing for the Hostity hotel & corporate TV platform.

- **Admin panel:** `www.pcncloud.in`
- **This showcase:** `go.pcncloud.in`
- **Root domain:** `pcncloud.in`

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
4. Domains → add **`go.pcncloud.in`**  
5. DNS: `CNAME` name `go` → `cname.vercel-dns.com` (or value Vercel shows)

Keep the admin project bound only to `www.pcncloud.in`.
