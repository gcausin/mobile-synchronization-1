DO
$do$

declare

    roleUserPk varchar;
    roleAdminPk varchar;
    userAdminPk varchar;
    userPublicPk varchar;
    userDemo1Pk varchar;
    userDemo2Pk varchar;
    recipePk varchar;
    
    recipeOwner varchar[];
    recipeOwners varchar[];
    recipeCounts integer[];
    
    recipeCounter integer := 1;

begin

    -- Comment next line in to reset complete database content
    -- All mobile clients must be reset, pk's will change
    /*
    delete from "~schema~"."Ingredient";
    delete from "~schema~"."Recipe";
    delete from "~schema~"."Food";
    delete from "~schema~"."UserRole";
    delete from "~schema~"."Account";
    delete from "~schema~"."Role";
    delete from "~schema~"."DeletedRecord";
    delete from "~schema~"."User";
    */

    -- User and admin role
    insert into "~schema~"."Role"(name) values('ROLE_USER') returning pk into roleUserPk;
    insert into "~schema~"."Role"(name) values('ROLE_ADMIN') returning pk into roleAdminPk;
    
    -- Four users
    insert into "~schema~"."User"(name) values('admin') returning pk into userAdminPk;
    insert into "~schema~"."User"(name) values('Public') returning pk into userPublicPk;
    insert into "~schema~"."User"(name) values('demo1') returning pk into userDemo1Pk;
    insert into "~schema~"."User"(name) values('demo2') returning pk into userDemo2Pk;
    
    -- Three accounts
    insert into "~schema~"."Account"(userFk, enabled, password) values(userAdminPk, true, 'admin');
    insert into "~schema~"."Account"(userFk, enabled, password) values(userDemo1Pk, true, 'demo1');
    insert into "~schema~"."Account"(userFk, enabled, password) values(userDemo2Pk, true, 'demo2');
    
    -- Roles
    insert into "~schema~"."UserRole"(userFk, roleFk) values(userAdminPk, roleAdminPk);
    insert into "~schema~"."UserRole"(userFk, roleFk) values(userAdminPk, roleUserPk);
    insert into "~schema~"."UserRole"(userFk, roleFk) values(userDemo1Pk, roleUserPk);
    insert into "~schema~"."UserRole"(userFk, roleFk) values(userDemo2Pk, roleUserPk);
    
    -- Eight foods
    ------------------------------------------------------------------------- name --- energy/kJ --
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Butter', 3143.36);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Fish', 321.86);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Bread', 902.88);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Milk', 271.7);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Water', 0);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Flour', 1433.74);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Egg', 647.9);
    insert into "~schema~"."Food"(userFk, name, energy) values(userPublicPk, 'Oil', 3762);
        
    -- Three owners of recipes, Public owns 20, Demo1 owns 4 and Demo2 owns 5 recipes
    recipeOwners := array[[userPublicPk, '20'],[userDemo1Pk, '4'],[userDemo2Pk, '5']];
    
    FOREACH recipeOwner SLICE 1 IN ARRAY recipeOwners LOOP

        FOR i IN 1..recipeOwner[2]::integer LOOP

            insert into "~schema~"."Recipe"(
                userFk,
                name)
            values(
                recipeOwner[1],
                --'Recipe '||clock_timestamp()::timestamp)
                'Recipe '||to_char(recipeCounter, '0000'))
            returning
                pk into recipePk;
                
            recipeCounter := recipeCounter + 1;
            
            -- insert a random set of foods with random number of entries and random amount as ingredients. 
            insert into "~schema~"."Ingredient"(
                recipeFk,
                foodFk,
                amount)
            select
                recipePk,
                pk,
                25 + random() * 75
            from
                (
                    select
                        cast(random() * count as integer) rand,
                        *
                    from
                        (
                            select
                                count(*) over (partition by 1) count,
                                pk
                            from
                                "~schema~"."Food"
                        ) as foods
                ) as randomFoods
            where
                rand % 2 = 1;
                
        END LOOP;

    END LOOP;

end

$do$;
