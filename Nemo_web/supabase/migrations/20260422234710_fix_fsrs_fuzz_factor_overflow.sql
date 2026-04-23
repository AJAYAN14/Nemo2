CREATE OR REPLACE FUNCTION public.fn_fuzz_factor_from_seed(p_seed bigint)
 RETURNS double precision
 LANGUAGE plpgsql
 IMMUTABLE
AS $function$
DECLARE
    t bigint;
    imul1 bigint;
    imul2 bigint;
BEGIN
    t := (p_seed + 1831561717) & 4294967295; -- 0x6D2B79F5
    
    imul1 := ((((t # (t >> 15))::numeric) * ((t | 1)::numeric)) % 4294967296::numeric)::bigint;
    t := imul1;
    
    imul2 := ((((t # (t >> 7))::numeric) * ((t | 61)::numeric)) % 4294967296::numeric)::bigint;
    t := (t # (t + imul2)) & 4294967295;
    
    RETURN ((t # (t >> 14)) & 4294967295)::float8 / 4294967296.0;
END;
$function$;
