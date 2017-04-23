using Generated.MobileSynchronization;
using Model.Base;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Newtonsoft.Json.Serialization;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public class RestService<T> : IRestService<T> where T : AbstractEntity, new()
    {
        public string Server { get; set; }

        HttpClient client;

        public List<T> Items { get; private set; }

        public RestService(SynchronizationParameters synchronizationParameters)
        {
            Server = synchronizationParameters.Server;
            var authData = string.Format("{0}:{1}", synchronizationParameters.Username, synchronizationParameters.Password);
            var authHeaderValue = Convert.ToBase64String(Encoding.UTF8.GetBytes(authData));

            client = new HttpClient();
            client.MaxResponseContentBufferSize = 256 * 100000;
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", authHeaderValue);
        }

        public async Task<List<T>> DownloadAsync(DateTime lastSyncTime, int pageCount, int pageSize)
        {
            Items = new List<T>();
            string listProperty = ListProperty();
            string timeAsString = lastSyncTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
            string requestString = Server +
                                listProperty +
                                "/search/findByModifiedDateGreaterThan?modifiedDate=" + timeAsString +
                                "&page=" + pageCount +
                                "&size=" + pageSize;
            Uri uri = new Uri(requestString);
            HttpResponseMessage response = await client.GetAsync(uri);

            if (response.IsSuccessStatusCode)
            {
                var content = await response.Content.ReadAsStringAsync();

                if (GeneratedConstants.LogJson)
                {
                    Debug.WriteLine(requestString + Environment.NewLine + JsonPrettify(content));
                }

                Items = JObject.Parse(content)["_embedded"][listProperty].Select(token =>
                {
                    T o = token.ToObject<T>();

                    o.Pk = token["_links"]["self"]["href"].ToObject<string>().Split('/').Last();

                    return o;
                }).ToList();
            }
            else
            {
                throw new Exception(response.StatusCode.ToString());
            }

            return Items;
        }

        private string JsonPrettify(string json)
        {
            using (var stringReader = new StringReader(json))
            using (var stringWriter = new StringWriter())
            {
                var jsonReader = new JsonTextReader(stringReader);
                var jsonWriter = new JsonTextWriter(stringWriter) { Formatting = Formatting.Indented };
                jsonWriter.WriteToken(jsonReader);
                return stringWriter.ToString();
            }
        }

        public async Task UploadAsync(object record, Action<Exception> exceptionHandler, string urlPath = null)
        {
            string listProperty = urlPath ?? ListProperty();
            Uri uri = new Uri(Server + listProperty);
            string json = JsonConvert.SerializeObject(record,
                                                      Formatting.Indented,
                                                      new JsonSerializerSettings { ContractResolver = new ShouldSerializeContractResolver() });

            if (GeneratedConstants.LogJson)
            {
                Debug.WriteLine(Server + listProperty + Environment.NewLine + json);
            }

            StringContent content = new StringContent(json, Encoding.UTF8, "application/hal+json");

            HttpResponseMessage response = await client.PostAsync(uri, content);

            if (!response.IsSuccessStatusCode)
            {
                throw new Exception(response.StatusCode.ToString());
            }
        }

        private string ListProperty()
        {
            return ListProperty(typeof(T));
        }

        public string ListProperty(Type type)
		{
			return FirstLetterToLowerCase(PluralOf(type.Name));
		}

		private string ListProperty(string name)
		{
			return FirstLetterToLowerCase(PluralOf(name));
		}

        private string FirstLetterToLowerCase(string text)
        {
            return Char.ToLowerInvariant(text[0]) + text.Substring(1);
        }

        private string PluralOf(string className)
        {
            if (new[] { "s", "x", "z", "ch", "sh" }.Any(e => className.EndsWith(e)))
            {
                return className + "es";
            }

            if (className.EndsWith("y") && !IsVowel(className[className.Length - 2]))
            {
                return className.Substring(0, className.Length - 1) + "ies";
            }

            return className + "s";
        }

        /**
         * Returns true if ch is a vowel.
         */
        private bool IsVowel(char ch)
        {
            return new[] { 'a', 'e', 'i', 'o', 'u' }.Any(c => c == ch);
        }
    }

    public class ShouldSerializeContractResolver : CamelCasePropertyNamesContractResolver
    {
        protected override JsonProperty CreateProperty(MemberInfo member, MemberSerialization memberSerialization)
        {
            JsonProperty property = base.CreateProperty(member, memberSerialization);

            if (property.PropertyName.EndsWith("Fk"))
            {
                property.ShouldSerialize = instance => false;
            }

            return property;
        }
    }
}
