-- State the service-only access model explicitly; mobile roles remain ungranted.
create policy bbco_server_access on public.bbco_documents
    for all to service_role using (true) with check (true);
create policy bbco_server_access on bbco_private.coordinators
    for all to service_role using (true) with check (true);
create policy bbco_server_access on bbco_private.write_receipts
    for all to service_role using (true) with check (true);

-- The project's RLS event-trigger helper must not be callable through the Data API.
-- Event triggers continue to run with their existing database-owner privileges.
do $$ begin
    if to_regprocedure('public.rls_auto_enable()') is not null then
        revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
    end if;
end $$;
