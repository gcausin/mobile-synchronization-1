using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Model.Base;
using MobileSyncModels.Services;
using Generated.Sync.Model.System;
using System.Diagnostics;
using MobileSyncModels.Model.System;
using System.Reflection;
using SQLiteNetExtensions.Extensions;
using System.Dynamic;
using Newtonsoft.Json;
using SQLite;
using Xamarin.Forms;
using Generated.MobileSynchronization;

[assembly: Xamarin.Forms.Dependency(typeof(SynchronizationService))]
namespace MobileSyncModels.Services
{
    public class EntitySync
    {
        public Action Up { get; set; }
        public Action Down { get; set; }
        public Action PostUp { get; set; }
        public Action PostDown { get; set; }
        public Type Type { get; set; }
        public Func<string, List<AbstractEntity>> Getter { get; set; }
        public Action<SQLiteConnection> CreateTable { get; set; }
        public Action<SQLiteConnection> DropTable { get; set; }
    }

    public partial class SynchronizationService : ISynchronizationService
    {
        private INotificationService notificationService;
        public INotificationService NotificationService
        {
            get { return notificationService ?? (notificationService = DependencyService.Get<INotificationService>()); }
        }

        private IDatabaseConnection databaseConnection;
        public IDatabaseConnection DatabaseConnection
        {
            get { return databaseConnection ?? (databaseConnection = DependencyService.Get<IDatabaseConnection>()); }
        }

        public EntitySync ForEntity<T>(SynchronizationParameters synchronizationParameters) where T : AbstractEntity, new()
        {
            Type type = typeof(T);
            string typeName = type.Name;
            EntitySync entitySync = new EntitySync
            {
                Type = type,
                Getter = sql => DatabaseConnection.Connection.Query<T>(sql).Cast<AbstractEntity>().ToList(),
            };

            if (typeName != "User" && synchronizationParameters.DontUpload.Contains(typeName))
            {
                entitySync.Up = () => entitySync.PostUp();
            }
            else
            {
                entitySync.Up = () => UploadAsync<T>(synchronizationParameters, entitySync.PostUp);
            }

            if (synchronizationParameters.DontDownload.Contains(typeName))
            {
                entitySync.Down = () => entitySync.PostDown();
            }
            else
            {
                entitySync.Down = () => DownloadAsync<T>(synchronizationParameters, entitySync.PostDown);
            }

            entitySync.CreateTable = connection => connection.CreateTable<T>();
            entitySync.DropTable = connection => connection.DropTable<T>();

            return entitySync;
        }

        public void Synchronize(SynchronizationParameters synchronizationParameters)
        {
            NotificationService.Send(NotificationEvent.PreSynchronization);

            List<EntitySync> syncTables = synchronizationParameters.EntitiesInSynchronization = Setup(synchronizationParameters);

            Debug.Assert(syncTables.Count >= 2, "syncEntities.Count >= 2");
            Debug.Assert(syncTables[0].Type == typeof(DeletedRecord), "syncEntities[0].Type == typeof(DeletedRecord)");
            Debug.Assert(syncTables[1].Type == typeof(User), "syncEntities[1].Type == typeof(User)");

            //synchronizationParameters.Notif((syncTables.Count >= 2) + "syncEntities.Count >= 2");
            //synchronizationParameters.Notif((syncTables[0].Type == typeof(DeletedRecord)) + "syncEntities[0].Type == typeof(DeletedRecord)");
            //synchronizationParameters.Notif((syncTables[1].Type == typeof(User)) +  "syncEntities[1].Type == typeof(User)");

            for (int i = 0; i < syncTables.Count; i++)
            {
                EntitySync sync = syncTables[i];

                // Handle each PostXxx Action different for last SyncEntity
                if (sync == syncTables.Last())
                {
                    // After last upload start with first SyncEntity download
                    sync.PostUp = syncTables[0].Down;
                    // After last download invoke finalAction
                    sync.PostDown = () =>
                    {
                        NotificationService.Send(NotificationEvent.Synchronized);
                        synchronizationParameters.FinalAction();
                    };
                }
                else
                {
                    // after up-/download start up-/download of next SyncEntity
                    sync.PostUp = syncTables[i + 1].Up;
                    sync.PostDown = syncTables[i + 1].Down;
                }
            }

            synchronizationParameters.EntitiesInSynchronization[1].PostUp = synchronizationParameters.EntitiesInSynchronization[0].Down;

            synchronizationParameters.Downloaded =
            synchronizationParameters.Uploaded =
            synchronizationParameters.RecordsToDelete =
            synchronizationParameters.RecordsDeleted =
            synchronizationParameters.RecordsDeletedAtServer = 0;

            synchronizationParameters.EntitiesInSynchronization[0].Up();
        }

        public IEnumerable<Type> AllEntities
        {
            get
            {
                return new[] { typeof(Synchronization), typeof(LastEntitySyncTime) }
                        .Union(Setup(new SynchronizationParameters()).Select(et => et.Type));
            }
        }

        public List<EntitySync> Entities
        {
            get
            {
                return Setup(new SynchronizationParameters());
            }
        }

        public void CreateTables()
        {
            DatabaseConnection.Connection.CreateTable<Synchronization>();
            DatabaseConnection.Connection.CreateTable<LastEntitySyncTime>();

            foreach (EntitySync syncTable in Setup(new SynchronizationParameters()))
            {
                syncTable.CreateTable(DatabaseConnection.Connection);
            }
        }

        public void DropTables()
        {
            List<EntitySync> syncTables = Setup(new SynchronizationParameters());

            syncTables.Reverse();

            foreach (EntitySync syncTable in syncTables)
            {
                syncTable.DropTable(DatabaseConnection.Connection);
            }

            DatabaseConnection.Connection.DropTable<LastEntitySyncTime>();
            DatabaseConnection.Connection.DropTable<Synchronization>();
        }

        private void UploadAsync<T>(SynchronizationParameters synchronizationParameters, Action finishedNotification) where T : AbstractEntity, new()
        {
            if (typeof(T) == typeof(DeletedRecord))
            {
                //synchronizationParameters.Notif("UploadAsync(DeletedRecord)");
                //DeleteAsync(synchronizationParameters, finishedNotification);
                finishedNotification();
                //synchronizationParameters.Notif("UploadAsync(DeletedRecord) finished");
            }
            else if (typeof(T) == typeof(User))
            {
                //synchronizationParameters.Notif("UploadAsync(User)");
                UploadAsyncSingleRequest(synchronizationParameters, finishedNotification);
                //synchronizationParameters.Notif("UploadAsync(User) finished");
            }
            else
            {
                Debug.Assert(false, "Unexpected type in upload: " + typeof(T).Name);
            }
        }

        class ToDelete
        {
            public string Entity { get; set; }
            public string Pk { get; set; }
        }

        private async void DeleteAsync(SynchronizationParameters synchronizationParameters, Action finishedNotification)
        {
            IRestService<User> restService = new RestService<User>(synchronizationParameters);

            try
            {
                await restService.UploadAsync(new List<ToDelete>(QueryEntities<DeletedRecord>(synchronizationParameters)
                                                    .Select(d => new ToDelete
                                                    {
                                                        Entity = d.EntityName.Split('.').Last(),
                                                        Pk = d.EntityPk
                                                    })), synchronizationParameters.ExceptionHandler, "users/singleRequestDelete");
                finishedNotification();
            }
            catch (Exception exception)
            {
                synchronizationParameters.ExceptionHandler(exception);
            }
        }

        private async void DownloadAsync<T>(SynchronizationParameters synchronizationParameters, Action finishedNotification) where T : AbstractEntity, new()
        {
            LastEntitySyncTime lastSyncRecord = GetLastSyncTime<T>(synchronizationParameters);
            RestService<T> restService = new RestService<T>(synchronizationParameters);
#if DEBUG
            Stopwatch watch = new Stopwatch();
            TimeSpan elapsed;

            watch.Start();
#endif

            try
            {
                List<T> allObjects = new List<T>();
                int pageCount = 0;
                bool readNextPage;

                do
                {
                    List<T> objects = await restService.DownloadAsync(lastSyncRecord.LastDownloadTime, pageCount, GeneratedConstants.DownloadPageSize);

#if DEBUG
                    if (GeneratedConstants.LogDebug)
                    {
                        elapsed = watch.Elapsed;
                        watch.Restart();
                        Debug.WriteLine("Download " + typeof(T).Name + " page=" + pageCount + ", size=" + GeneratedConstants.DownloadPageSize + " needed " + elapsed);
                    }
#endif

                    readNextPage = objects.Count == GeneratedConstants.DownloadPageSize;
                    allObjects.AddRange(objects);
                    pageCount++;
                } while (readNextPage);

                // Fire and forget: Task.Factory.StartNew(() => serviceControl.Execute());

                await Task.Factory.StartNew(() =>
                {
                    Stopwatch refreshWatch = new Stopwatch();

                    refreshWatch.Start();

                    if (typeof(T) == typeof(DeletedRecord) && allObjects.Count > 0)
                    {
                        foreach (T obj in allObjects)
                        {
                            synchronizationParameters.RecordsToDelete++;

                            DeletedRecord deletedRecord = obj as DeletedRecord;

                            int deletedRecords = DatabaseConnection
                                                    .Connection
                                                    .Execute("delete from [" + deletedRecord.EntityName + "] where [Pk] = ?",
                                                        new[] { deletedRecord.EntityPk });

                            if (synchronizationParameters.Refresh != null && refreshWatch.Elapsed >= TimeSpan.FromSeconds(1))
                            {
                                synchronizationParameters.Refresh();
                                refreshWatch.Restart();
                            }

                            synchronizationParameters.RecordsDeleted += deletedRecords;
                        }

                        if (allObjects.Count > 0)
                        {
                            lastSyncRecord.LastDownloadTime = allObjects.Last().ModifiedDate;
                        }

                        DatabaseConnection.Connection.Execute("delete from DeletedRecord where EntityPk in (" +
                                            allObjects.Select(o => "'" + (o as DeletedRecord).EntityPk + "'").Aggregate((i, j) => i + "," + j) + ")");

                        if (synchronizationParameters.Refresh != null && refreshWatch.Elapsed >= TimeSpan.FromSeconds(1))
                        {
                            synchronizationParameters.Refresh();
                            refreshWatch.Restart();
                        }
#if DEBUG
                        if (GeneratedConstants.LogDebug)
                        {
                            elapsed = watch.Elapsed;
                            watch.Restart();
                            Debug.WriteLine("Performing delete actions, count=" + allObjects.Count + " needed " + elapsed);
                        }
#endif
                    }
                    else
                    {
                        foreach (T obj in allObjects)
                        {
                            try
                            {
                                DatabaseConnection.Connection.Insert(obj);
                            }
                            catch (Exception)
                            {
                                DatabaseConnection.Connection.GetChildren(obj);
                                obj.IsPending = false;
                                DatabaseConnection.Connection.Update(obj);
                            }

                            synchronizationParameters.Downloaded++;

                            if (synchronizationParameters.Refresh != null && refreshWatch.Elapsed >= TimeSpan.FromSeconds(1))
                            {
                                synchronizationParameters.Refresh();
                                refreshWatch.Restart();
                            }
                        }

                        if (allObjects.Count > 0)
                        {
                            lastSyncRecord.LastDownloadTime = allObjects.Last().ModifiedDate;
                        }

#if DEBUG
                        if (GeneratedConstants.LogDebug)
                        {
                            elapsed = watch.Elapsed;
                            watch.Restart();
                            Debug.WriteLine("Insert/update actions, count=" + allObjects.Count + " needed " + elapsed);
                        }
#endif
                    }
                });

                finishedNotification();
                UpdateLastSyncTime(synchronizationParameters, lastSyncRecord);
            }
            catch (Exception exception)
            {
                Debug.WriteLine(exception.StackTrace);
                ExceptionHandler(synchronizationParameters, exception);
            }
            finally
            {
                synchronizationParameters.Refresh?.Invoke();
            }
        }

        private void ExceptionHandler(SynchronizationParameters synchronizationParameters, Exception exception)
        {
            NotificationService.Send(NotificationEvent.SynchronizationFailed);
            synchronizationParameters.ExceptionHandler(exception);
        }

        private void UpdateLastSyncTime(SynchronizationParameters synchronizationParameters, LastEntitySyncTime lastSyncObj)
        {
            DatabaseConnection.Connection.Update(lastSyncObj);
        }
        private async void UploadAsyncSingleRequest(SynchronizationParameters synchronizationParameters, Action finishedNotification)
        {
            Stopwatch watch = new Stopwatch();

            watch.Start();

            IRestService<User> restService = new RestService<User>(synchronizationParameters);
            IDictionary<string, object> underlyingRoot = new ExpandoObject();
            bool exceptionOccured = false;
            List<AbstractEntity> allEntities = new List<AbstractEntity>();
            int entitiesInRequest = 0;
            int requestCount = 0;

            foreach (EntitySync entity in synchronizationParameters.EntitiesInSynchronization)
            {
                string entityName = entity.Type.Name;

                if (synchronizationParameters.DontUpload.Contains(entityName))
                {
                    continue;
                }

                List<AbstractEntity> entities = entity.Getter("select * from " + entityName + " where IsPending=1 order by ModifiedDate");

                if (entities.Count == 0)
                {
                    continue;
                }

                allEntities.AddRange(entities);

                Dictionary<string, PropertyInfo> foreignKeys = GetForeignKeyProperties(entity.Type);
                var grouped = entities
                                .GroupBy(e => foreignKeys.Count == 0 ?
                                    "" :
                                    foreignKeys.Select(f => f.Value.GetValue(e)).Aggregate((i, j) => i + "," + j))
                                .ToDictionary(g =>
                                {
                                    Dictionary<string, string> keys = new Dictionary<string, string>();
                                    string[] values = g.Key.ToString().Split(',');
                                    int index = 0;

                                    foreach (KeyValuePair<string, PropertyInfo> kvp in foreignKeys)
                                    {
                                        keys.Add(kvp.Key, values[index++]);
                                    }

                                    return keys;
                                }, g => g);

                Debug.WriteLine("now " + entityName + ", until now " + allEntities.Count + " entities");

                if (entitiesInRequest + entities.Count > GeneratedConstants.UploadPageSize)
                {
                    List<object> l = new List<object>();

                    underlyingRoot.Add(restService.ListProperty(entity.Type), l);

                    foreach (var group in grouped)
                    {
                        int groupCount = group.Value.Count();
                        List<int[]> pieces = CutIntoPieces(entitiesInRequest, groupCount);

                        if (pieces.Count == 0)
                        {
                            l.Add(new
                            {
                                ForeignKeys = group.Key,
                                Values = group.Value
                            });
                            entitiesInRequest += group.Value.Count();
                        }
                        else
                        {
                            foreach (int[] piece in pieces)
                            {
                                l.Add(new
                                {
                                    ForeignKeys = group.Key,
                                    Values = group.Value.Skip(piece[0]).Take(piece[1])
                                });

                                await UploadAsync(
                                                restService,
                                                underlyingRoot,
                                                exception => { exceptionOccured = true; ExceptionHandler(synchronizationParameters, exception); }, requestCount++);

                                if (exceptionOccured)
                                {
                                    return;
                                }

                                underlyingRoot = new ExpandoObject();
                                underlyingRoot.Add(restService.ListProperty(entity.Type), l = new List<object>());
                            }

                            entitiesInRequest = pieces.Last()[1];
                        }
                    }
                }
                else
                {
                    underlyingRoot.Add(restService.ListProperty(entity.Type), grouped.Select(g => new
                    {
                        ForeignKeys = g.Key,
                        Values = g.Value
                    }));
                    entitiesInRequest += entities.Count;
                }
            }

            if (!exceptionOccured)
            {
                await UploadAsync(
                            restService,
                            underlyingRoot,
                            exception => { exceptionOccured = true; ExceptionHandler(synchronizationParameters, exception); }, requestCount++);
            }

            if (!exceptionOccured)
            {
                Stopwatch watchUpdatePending = new Stopwatch();

                watchUpdatePending.Start();

                foreach (var group in allEntities.GroupBy(e => e.GetType()))
                {
                    DatabaseConnection.Connection.Execute("update " + group.Key.Name + " set IsPending = 0 where Pk in (" +
                                group.Select(e => "'" + e.Pk + "'").Aggregate((i, j) => i + "," + j) + ")");
                }

                //foreach (AbstractEntity entity in allEntities)
                //{
                //    entity.IsPending = false;
                //    db.Update(entity);
                //}

                Debug.WriteLine("watchUpdatePending needed: " + watchUpdatePending.Elapsed);

                synchronizationParameters.Uploaded = allEntities.Count;
                finishedNotification();

                return;
            }
        }

        private Dictionary<string, PropertyInfo> GetForeignKeyProperties(Type type)
        {
            TypeInfo typeInfo = type.GetTypeInfo();

            return typeInfo
                    .DeclaredProperties
                    .Where(p =>
                        p.GetCustomAttributes(typeof(JsonIgnoreAttribute), true).FirstOrDefault() as JsonIgnoreAttribute == null &&
                        p.Name.EndsWith("Fk"))
                    .ToDictionary(p => p.Name, p => p);
        }

        private async Task<Tuple<int, bool>> UploadAsync(
                                        IRestService<User> restService,
                                        object underlyingRoot,
                                        Action<Exception> exceptionHandler,
                                        int i)
        {
            Debug.WriteLine("working (delete) ..{0}", i);

            try
            {
                await restService.UploadAsync(underlyingRoot, exceptionHandler, "users/singleRequest");
            }
            catch(Exception exception)
            {
                exceptionHandler(exception);
            }

            return Tuple.Create(i, true);
        }

        List<int[]> CutIntoPieces(int entitiesInrequest, int groupCount)
        {
            if (entitiesInrequest + groupCount <= GeneratedConstants.UploadPageSize)
            {
                return new List<int[]>();
            }

            int rest = GeneratedConstants.UploadPageSize - entitiesInrequest;
            int start = 0;
            List<int[]> list = new List<int[]>
            {
                new[] { start, rest }
            };

            groupCount -= rest;

            int blocks = groupCount / GeneratedConstants.UploadPageSize;

            for (int i = 0; i < blocks; i++)
            {
                list.Add(new int[] { rest + i * GeneratedConstants.UploadPageSize, GeneratedConstants.UploadPageSize });
            }

            int remaining = groupCount % GeneratedConstants.UploadPageSize;

            if (rest > 0)
            {
                list.Add(new int[] { rest + blocks * GeneratedConstants.UploadPageSize, remaining });
            }

            return list;
        }

        private LastEntitySyncTime GetLastSyncTime<T>(SynchronizationParameters synchronizationParameters) where T : AbstractEntity, new()
        {
            string entityName = typeof(T).Name;
            LastEntitySyncTime lastSyncObj = DatabaseConnection.Connection.Query<LastEntitySyncTime>("select * from [LastEntitySyncTime] " +
                                                                           "where [EntityName] = ?", new[] { entityName }).FirstOrDefault();

            if (lastSyncObj == null)
            {
                List<T> entities = DatabaseConnection.Connection.Query<T>("select * from [" + entityName + "]");
                DateTime lastSyncTime = entities.Count == 0 ? default(DateTime) : entities.Select(o => o.ModifiedDate).Max();

                DatabaseConnection.Connection.Insert(lastSyncObj = new LastEntitySyncTime { EntityName = entityName, LastDownloadTime = lastSyncTime });
            }

            return lastSyncObj;
        }

        private List<T> QueryEntities<T>(SynchronizationParameters synchronizationParameters) where T : AbstractEntity, new()
        {
            return DatabaseConnection.Connection.Query<T>("select * from [" + typeof(T).Name + "] where IsPending = 1 order by ModifiedDate");
        }

        public void Reset()
        {
            DropTables();
            CreateTables();
            NotificationService.Send(NotificationEvent.Reset);
        }
    }
}
